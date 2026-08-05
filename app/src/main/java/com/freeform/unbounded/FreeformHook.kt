package com.freeform.unbounded

import android.content.SharedPreferences
import android.graphics.Rect
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/** Modern LibXposed API 102 entry point for HyperOS 3 freeform-window hooks. */
class FreeformHook : XposedModule() {
    private val installedHookIds = ConcurrentHashMap.newKeySet<String>()
    private val logCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val horizontalDragSession = ThreadLocal<HorizontalDragSession?>()
    private val loadedConfig = AtomicReference<HookConfig?>()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(
            Log.INFO,
            TAG,
            "Loaded in ${param.processName}; framework=$frameworkName/$frameworkVersionCode api=$apiVersion"
        )
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != PACKAGE_SYSTEM_UI || !param.isFirstPackage) return
        // The boundary is intentionally a SystemUI-only hook. Cache the remote
        // preference for this process so changing the setting never mutates an
        // already-running window manager; restart SystemUI to load a new value.
        loadedConfig.set(readConfig())
        installProfiles(HookProfiles.systemUi, param.classLoader, PACKAGE_SYSTEM_UI)
    }

    override fun onHotReloading(param: HotReloadingParam): Boolean {
        log(Log.INFO, TAG, "Hot reload requested by ${param.extras?.getString("source", "unknown")}")
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        log(Log.INFO, TAG, "Hot reloaded with ${param.oldHookHandles.size} old hook(s)")
        param.oldHookHandles.forEach { handle ->
            val action = parseAction(handle.id)
            if (action == null) {
                runCatching { handle.unhook() }
                return@forEach
            }
            runCatching {
                handle.replaceHook(createReloadHooker(handle.executable, action))
            }.onFailure { error ->
                logLimited(
                    Log.ERROR,
                    "reload:${handle.id}",
                    "Failed to replace ${handle.id}",
                    error,
                )
            }
        }
    }

    private fun installProfiles(
        profiles: List<ClassHookProfile>,
        classLoader: ClassLoader,
        process: String,
    ) {
        var installed = 0
        profiles.forEach { profile ->
            val targetClass = try {
                Class.forName(profile.className, false, classLoader)
            } catch (_: ClassNotFoundException) {
                logLimited(
                    Log.INFO,
                    "missing:${profile.className}",
                    "$process: profile class not found: ${profile.className}",
                )
                return@forEach
            } catch (error: LinkageError) {
                logLimited(
                    Log.WARN,
                    "link:${profile.className}",
                    "Cannot link ${profile.className}",
                    error,
                )
                return@forEach
            }

            var matchedInClass = 0
            targetClass.declaredMethods.forEach { method ->
                val rule = profile.rules.firstOrNull { it.matches(method) } ?: return@forEach
                matchedInClass++
                if (installHook(profile, method, rule)) installed++
            }
            if (matchedInClass == 0) {
                val candidates = targetClass.declaredMethods
                    .asSequence()
                    .filter(::isAdaptationCandidate)
                    .take(MAX_DIAGNOSTIC_METHODS)
                    .joinToString("; ") { methodSignature(it) }
                    .ifBlank { "none" }
                logLimited(
                    Log.WARN,
                    "unmatched:${profile.className}",
                    "$process: class exists but no known signature matched: ${profile.className}; " +
                        "candidate methods=$candidates",
                )
            }
        }

        log(
            if (installed > 0) Log.INFO else Log.WARN,
            TAG,
            "$process: installed $installed HyperOS 3 hook(s)"
        )
    }

    private fun installHook(
        profile: ClassHookProfile,
        method: Method,
        rule: MethodHookRule,
    ): Boolean {
        val hookId = "freeform-unbounded:${profile.className}#${methodSignature(method)}:${rule.action}"
        if (!installedHookIds.add(hookId)) return false

        return try {
            hook(method)
                .setId(hookId)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(createHooker(profile, method, rule, hookId))
            log(Log.INFO, TAG, "Hooked ${profile.className}#${methodSignature(method)}")
            true
        } catch (error: Throwable) {
            installedHookIds.remove(hookId)
            logLimited(Log.ERROR, "install:$hookId", "Failed to hook $hookId", error)
            false
        }
    }

    private fun createHooker(
        profile: ClassHookProfile,
        method: Method,
        rule: MethodHookRule,
        hookId: String,
    ): XposedInterface.Hooker {
        return XposedInterface.Hooker { chain ->
            handleInterception(profile, method, rule, hookId, chain)
        }
    }

    private fun createReloadHooker(
        executable: Executable,
        action: HookAction,
    ): XposedInterface.Hooker {
        val method = executable as? Method
            ?: return XposedInterface.Hooker { chain -> chain.proceed() }
        val profile = ClassHookProfile(method.declaringClass.name, emptyList())
        val rule = MethodHookRule(setOf(method.name), action)
        val hookId = "reload:${method.declaringClass.name}#${methodSignature(method)}"
        return createHooker(profile, method, rule, hookId)
    }

    private fun handleInterception(
        profile: ClassHookProfile,
        method: Method,
        rule: MethodHookRule,
        hookId: String,
        chain: XposedInterface.Chain,
    ): Any? {
        val config = loadedConfig.get() ?: readConfig().also { loadedConfig.compareAndSet(null, it) }

        return when (rule.action) {
            HookAction.DISABLE_BOOLEAN -> {
                logLimited(
                    Log.INFO,
                    hookId,
                    "Disabled ${profile.className.substringAfterLast('.')}#${method.name}"
                )
                false
            }

            HookAction.FREE_HORIZONTAL_FRICTION -> {
                val dragTarget = chain.getArg(2) as? Rect
                val isMini = chain.getArg(5) as? Boolean ?: false
                val result = chain.proceed()
                if (result !is Rect || dragTarget == null || isMini) {
                    result
                } else {
                    val adjusted = BoundsPolicy.keepHorizontalDragTarget(result, dragTarget)
                    val session = horizontalDragSession.get()
                    // applyFriction does not carry task scale in its signature. If it
                    // is called inside the drag-session hook, use that scale; the
                    // final-bounds hook always performs the complete correction.
                    val safe = session?.let {
                        applyHorizontalResistance(adjusted, config, it.scale.scaleX)
                    } ?: adjusted
                    logLimited(
                        Log.DEBUG,
                        hookId,
                        "Released horizontal friction ${methodSignature(method)}: $result -> $safe",
                    )
                    safe
                }
            }

            HookAction.PRESERVE_HORIZONTAL_DRAG_SESSION -> {
                val (taskInfo, dragTarget) = when (method.name) {
                    "adjustBoundsAndScalePostUpdate" -> {
                        chain.getArg(2) to (chain.getArg(1) as? Rect)
                    }
                    "adjustFreeformBoundsAndScale" -> {
                        chain.getArg(0) to (chain.getArg(2) as? Rect)
                    }
                    else -> null to null
                }
                if (dragTarget == null || !shouldPreserveHorizontalDrag(taskInfo)) {
                    chain.proceed()
                } else {
                    val previous = horizontalDragSession.get()
                    horizontalDragSession.set(
                        HorizontalDragSession(
                            target = Rect(dragTarget),
                            scale = resolveScale(taskInfo),
                        ),
                    )
                    try {
                        logLimited(
                            Log.DEBUG,
                            hookId,
                            "Started horizontal drag session ${methodSignature(method)}: $dragTarget",
                        )
                        chain.proceed()
                    } finally {
                        if (previous == null) {
                            horizontalDragSession.remove()
                        } else {
                            horizontalDragSession.set(previous)
                        }
                    }
                }
            }

            HookAction.PRESERVE_HORIZONTAL_STABLE_OFFSET -> {
                val session = horizontalDragSession.get()
                val dragTarget = session?.target
                val currentBounds = chain.getArg(0) as? Rect
                if (session == null || currentBounds == null) {
                    chain.proceed()
                } else {
                    val result = chain.proceed()
                    val clamped = Rect(currentBounds)
                    currentBounds.set(BoundsPolicy.keepHorizontalDragTarget(currentBounds, session.target))
                    currentBounds.set(applyHorizontalResistance(currentBounds, config, session.scale.scaleX))
                    logLimited(
                        Log.DEBUG,
                        hookId,
                        "Preserved horizontal stable offset ${methodSignature(method)}: " +
                            "$clamped -> $currentBounds",
                    )
                    result
                }
            }

            HookAction.PRESERVE_HORIZONTAL_ANIM_TARGET_PARAM -> {
                val session = horizontalDragSession.get()
                val dragTarget = session?.target
                val targetBounds = chain.getArg(0) as? Rect
                if (session == null || targetBounds == null) {
                    chain.proceed()
                } else {
                    val original = Rect(targetBounds)
                    targetBounds.set(BoundsPolicy.keepHorizontalDragTarget(targetBounds, session.target))
                    targetBounds.set(applyHorizontalResistance(targetBounds, config, session.scale.scaleX))
                    logLimited(
                        Log.DEBUG,
                        hookId,
                        "Preserved horizontal animation target ${methodSignature(method)}: " +
                            "$original -> $targetBounds",
                    )
                    chain.proceed()
                }
            }

            HookAction.PRESERVE_HORIZONTAL_MOVE_FINAL_BOUNDS -> {
                val taskInfo = chain.getArg(0)
                val actionMode = chain.getArg(1) as? Int ?: -1
                val x = chain.getArg(2) as? Float
                val y = chain.getArg(3) as? Float
                val downPoint = chain.getArg(6) as? android.graphics.PointF
                val downBounds = invokeRectNoArg(taskInfo, "getDownBounds")?.let(::Rect)
                val result = chain.proceed()
                if (
                    result !is Rect ||
                    x == null ||
                    y == null ||
                    downPoint == null ||
                    downBounds == null ||
                    !shouldPreserveHorizontalDrag(taskInfo)
                ) {
                    result
                } else {
                    val dragTarget = Rect(downBounds)
                    dragTarget.offset(
                        (x - downPoint.x).roundToInt(),
                        (y - downPoint.y).roundToInt(),
                    )
                    // MIUI's getMoveBounds/getUpBounds clamp both axes to its own
                    // inset movable rectangle. Rebuild the requested position for
                    // every move frame, then apply our visible-edge policy. This
                    // keeps vertical dragging live while still enforcing the
                    // minimum visible distance at the edge.
                    val adjusted = BoundsPolicy.keepRequestedDragTarget(result, dragTarget)
                    val scale = resolveScale(taskInfo)
                    val safe = if (actionMode == ACTION_MODE_UP) {
                        // Release: return to the strict boundary. MIUI's existing
                        // Folme transition animates from the resisted position to
                        // this target, producing the native spring-back motion.
                        applySafety(adjusted, config, scale)
                    } else {
                        // Move: permit a small, progressively damped overshoot.
                        applyDragResistance(adjusted, config, scale)
                    }
                    logLimited(
                        Log.DEBUG,
                        hookId,
                        "Preserved horizontal move final bounds ${methodSignature(method)}: " +
                            "$result -> $safe target=$dragTarget",
                    )
                    safe
                }
            }
        }
    }

    private fun readConfig(): HookConfig {
        val prefs = runCatching { getRemotePreferences(ModuleConfigKeys.GROUP) }.getOrNull()
            ?: return HookConfig()
        return HookConfig(
            securityMarginPx = normalizeSecurityMargin(prefs.getInt(
                ModuleConfigKeys.SECURITY_MARGIN,
                ModuleConfigKeys.DEFAULT_MARGIN,
            )),
        )
    }

    private fun applySafety(
        result: Rect,
        config: HookConfig,
        scale: WindowScale = WindowScale.UNSCALED,
    ): Rect {
        val screen = screenRect() ?: return result
        return BoundsPolicy.keepInnerEdgesVisible(
            result,
            screen,
            config.securityMarginPx,
            scale.scaleX,
            scale.scaleY,
        )
    }

    private fun applyHorizontalSafety(
        result: Rect,
        config: HookConfig,
        scaleX: Float,
    ): Rect {
        val screen = screenRect() ?: return result
        val safe = BoundsPolicy.keepInnerEdgesVisible(
            result,
            screen,
            config.securityMarginPx,
            scaleX,
            1f,
        )
        return Rect(safe.left, result.top, safe.right, result.bottom)
    }

    private fun applyHorizontalResistance(
        result: Rect,
        config: HookConfig,
        scaleX: Float,
    ): Rect {
        val screen = screenRect() ?: return result
        val resisted = BoundsPolicy.resistBeyondInnerEdges(
            result,
            screen,
            config.securityMarginPx,
            scaleX,
            1f,
            maxOverscrollPx = (config.securityMarginPx / 2).coerceIn(48, 128),
        )
        return Rect(resisted.left, result.top, resisted.right, result.bottom)
    }

    private fun applyDragResistance(
        result: Rect,
        config: HookConfig,
        scale: WindowScale,
    ): Rect {
        val screen = screenRect() ?: return result
        return BoundsPolicy.resistBeyondInnerEdges(
            result,
            screen,
            config.securityMarginPx,
            scale.scaleX,
            scale.scaleY,
            maxOverscrollPx = (config.securityMarginPx / 2).coerceIn(48, 128),
        )
    }

    private fun screenRect(): Rect? = runCatching {
        val metrics = android.content.res.Resources.getSystem().displayMetrics
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            null
        } else {
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }.getOrNull()

    private fun methodSignature(method: Method): String = buildString {
        append(method.name)
        append('(')
        append(method.parameterTypes.joinToString(",") { it.name })
        append("):")
        append(method.returnType.name)
    }

    private fun isAdaptationCandidate(method: Method): Boolean {
        val name = method.name.lowercase()
        return method.returnType == Boolean::class.javaPrimitiveType ||
            method.returnType == Rect::class.java ||
            ADAPTATION_NAME_PARTS.any(name::contains)
    }

    private fun parseAction(hookId: String?): HookAction? {
        if (hookId == null) return null
        val actionName = hookId.substringAfterLast(':', "")
        return runCatching { HookAction.valueOf(actionName) }.getOrNull()
    }

    private fun logLimited(
        priority: Int,
        key: String,
        message: String,
        error: Throwable? = null,
    ) {
        val count = logCounts.computeIfAbsent(key) { AtomicInteger() }.incrementAndGet()
        if (count > MAX_LOGS_PER_KEY) return
        val suffix = if (count == MAX_LOGS_PER_KEY) " (further messages suppressed)" else ""
        if (error == null) log(priority, TAG, message + suffix) else log(priority, TAG, message + suffix, error)
    }

    private fun shouldPreserveHorizontalDrag(taskInfo: Any?): Boolean {
        if (taskInfo == null) return false
        return !PIN_OR_MINI_STATE_METHODS.any { methodName -> invokeBooleanNoArg(taskInfo, methodName) }
    }

    private fun invokeBooleanNoArg(target: Any, methodName: String): Boolean = runCatching {
        val method = target.javaClass.methods.firstOrNull { method ->
            method.name == methodName &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Boolean::class.javaPrimitiveType
        } ?: return@runCatching false
        method.invoke(target) as? Boolean ?: false
    }.getOrDefault(false)

    private fun invokeRectNoArg(target: Any?, methodName: String): Rect? = runCatching {
        if (target == null) return@runCatching null
        val method = target.javaClass.methods.firstOrNull { method ->
            method.name == methodName &&
                method.parameterTypes.isEmpty() &&
                method.returnType == Rect::class.java
        } ?: return@runCatching null
        method.invoke(target) as? Rect
    }.getOrNull()

    /**
     * MIUI has moved the freeform scale between task-info and Folme classes across
     * HyperOS releases. Read the public accessor first, then fields, and inspect a
     * small set of known nested holders. A failed lookup safely falls back to 1x.
     */
    private fun resolveScale(target: Any?): WindowScale {
        val direct = readScale(target)
        if (direct != null) return direct
        val nestedNames = listOf(
            "getInfo",
            "getTaskInfo",
            "getFolmeControl",
            "getFolme",
            "getAnimationTarget",
            "getBaseAnimTarget",
        )
        for (name in nestedNames) {
            val nested = invokeObjectNoArg(target, name) ?: continue
            readScale(nested)?.let { return it }
        }
        return WindowScale.UNSCALED
    }

    private fun readScale(target: Any?): WindowScale? {
        if (target == null) return null
        val x = readFloatNoArg(target, SCALE_X_METHODS)
            ?: readFloatField(target, SCALE_X_FIELDS)
        val y = readFloatNoArg(target, SCALE_Y_METHODS)
            ?: readFloatField(target, SCALE_Y_FIELDS)
        val uniform = readFloatNoArg(target, SCALE_METHODS)
            ?: readFloatField(target, SCALE_FIELDS)
        val scaleX = (x ?: uniform)?.takeIf(::isValidScale) ?: return null
        val scaleY = (y ?: uniform)?.takeIf(::isValidScale) ?: scaleX
        return WindowScale(scaleX, scaleY)
    }

    private fun invokeObjectNoArg(target: Any?, methodName: String): Any? = runCatching {
        if (target == null) return@runCatching null
        val method = target.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterTypes.isEmpty()
        } ?: return@runCatching null
        method.invoke(target)
    }.getOrNull()

    private fun readFloatNoArg(target: Any, names: Set<String>): Float? = runCatching {
        val method = target.javaClass.methods.firstOrNull {
            it.name in names && it.parameterTypes.isEmpty() &&
                (it.returnType == Float::class.javaPrimitiveType ||
                    it.returnType == Float::class.java ||
                    it.returnType == Double::class.javaPrimitiveType ||
                    it.returnType == Double::class.java)
        } ?: return@runCatching null
        (method.invoke(target) as? Number)?.toFloat()
    }.getOrNull()

    private fun readFloatField(target: Any, names: Set<String>): Float? = runCatching {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull {
                it.name in names &&
                    (it.type == Float::class.javaPrimitiveType ||
                        it.type == Float::class.java ||
                        it.type == Double::class.javaPrimitiveType ||
                        it.type == Double::class.java)
            }
            if (field != null) {
                field.isAccessible = true
                return@runCatching (field.get(target) as? Number)?.toFloat()
            }
            type = type.superclass
        }
        null
    }.getOrNull()

    private fun isValidScale(value: Float): Boolean =
        value.isFinite() && value > 0.01f && value <= 4f

    private data class HookConfig(
        val securityMarginPx: Int = ModuleConfigKeys.DEFAULT_MARGIN,
    )

    private data class HorizontalDragSession(
        val target: Rect,
        val scale: WindowScale,
    )

    private data class WindowScale(
        val scaleX: Float,
        val scaleY: Float,
    ) {
        companion object {
            val UNSCALED = WindowScale(1f, 1f)
        }
    }

    companion object {
        private const val TAG = "FreeformUnbounded"
        private const val PACKAGE_SYSTEM_UI = "com.android.systemui"
        private const val MAX_LOGS_PER_KEY = 3
        private const val MAX_DIAGNOSTIC_METHODS = 30
        private const val ACTION_MODE_UP = 1
        private val SCALE_X_METHODS = setOf("getScaleX", "scaleX")
        private val SCALE_Y_METHODS = setOf("getScaleY", "scaleY")
        private val SCALE_METHODS = setOf("getTaskScale", "getScale", "taskScale", "scale")
        private val SCALE_X_FIELDS = setOf("scaleX", "mScaleX", "destinationScaleX")
        private val SCALE_Y_FIELDS = setOf("scaleY", "mScaleY", "destinationScaleY")
        private val SCALE_FIELDS = setOf("taskScale", "mTaskScale", "scale", "mScale")
        private val ADAPTATION_NAME_PARTS = listOf(
            "freeform", "pin", "constraint", "bound", "move", "accessible", "limit",
        )
        private val PIN_OR_MINI_STATE_METHODS = listOf(
            "isMiniState",
            "isMiniPinedState",
            "isNormalPinedState",
            "isFreeformEludeAnimation",
        )
    }
}
