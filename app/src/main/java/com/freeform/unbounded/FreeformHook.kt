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
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Enumeration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/** Modern LibXposed API 102 entry point for the freeform and AOD hooks. */
class FreeformHook : XposedModule() {
    private val installedHookIds = ConcurrentHashMap.newKeySet<String>()
    private val logCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val horizontalDragSession = ThreadLocal<HorizontalDragSession?>()
    private val loadedConfig = AtomicReference<HookConfig?>()
    private val dynamicGlassWallpaper = ThreadLocal.withInitial { false }
    private val currentProcessName = AtomicReference<String?>(null)
    // ConcurrentHashMap is loaded by the boot classloader, so its hook cannot
    // resolve MiWallpaper classes through receiver.javaClass.classLoader.
    // Cache the controller's actual r object while an app-classloader hook is
    // running, then compare map operations by identity.
    private val wallpaperListenerMap = AtomicReference<Any?>(null)
    private val runtimeEventSequence = AtomicLong(0)

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        currentProcessName.set(param.processName)
        log(
            Log.INFO,
            TAG,
            "Loaded in ${param.processName}; module=v${BuildConfig.VERSION_NAME} " +
                "(${BuildConfig.VERSION_CODE}); framework=$frameworkName/$frameworkVersionCode api=$apiVersion"
        )
        if (isSuperWallpaperProcess(param.processName)) {
            // Some HyperOS wallpaper child processes expose the module entry but
            // do not deliver PackageReady. Install against the process class
            // loader here so the render-side frame producer is still covered.
            val loader = Thread.currentThread().contextClassLoader
                ?: FreeformHook::class.java.classLoader
                ?: ClassLoader.getSystemClassLoader()
            log(Log.INFO, TAG, "Super wallpaper child profile install from onModuleLoaded; " +
                "loader=${loader.javaClass.name}")
            val nativeInstalled = NativeDepthBridge.install(readConfig().superWallpaperDepthEnabled)
            log(Log.INFO, TAG, "CI21 native EGL depth bridge installed=$nativeInstalled")
            installWallpaperProfiles(loader, "${param.processName}@onModuleLoaded")
        }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        val process = currentProcessName.get().orEmpty()
        log(
            Log.INFO,
            TAG,
            "Package ready package=${param.packageName} process=$process loader=${param.classLoader.javaClass.name}",
        )
        when {
            param.packageName == PACKAGE_SYSTEM_UI -> {
                // The boundary is intentionally a SystemUI-only hook. Cache the
                // remote preference for this process so changing the setting never
                // mutates an already-running window manager.
                loadedConfig.set(readConfig())
                installClockMaskEffectObserver()
                // Register before any depth hook is called. The wallpaper child
                // process can publish its first frame during SystemUI startup,
                // before isDepthVideoEnable() is reached.
                VideoDepthPolicy.contextOf(null)?.let(SuperWallpaperFrameBridge::installSystemUiReceiver)
                installProfiles(HookProfiles.systemUi, param.classLoader, PACKAGE_SYSTEM_UI)
            }
            param.packageName == PACKAGE_AOD -> installProfiles(
                HookProfiles.aod,
                param.classLoader,
                "$PACKAGE_AOD@${currentProcessName.get().orEmpty()}",
            )
            isSuperWallpaperProcess(process) || param.packageName.startsWith("$PACKAGE_MI_WALLPAPER.") ->
                installWallpaperProfiles(param.classLoader, "${param.packageName}@$process")
            param.packageName == PACKAGE_MI_WALLPAPER -> installProfiles(
                HookProfiles.miWallpaper,
                param.classLoader,
                "$PACKAGE_MI_WALLPAPER@$process",
            )
        }
    }

    private fun installWallpaperProfiles(classLoader: ClassLoader, process: String) {
        installWallpaperFrameSyncObserver()
        NativeDepthBridge.install(readConfig().superWallpaperDepthEnabled)
        installProfiles(HookProfiles.miWallpaper, classLoader, process)
        installDiscoveredSuperWallpaperProfiles(classLoader, process)
    }

    private fun installWallpaperFrameSyncObserver() {
        if (!readConfig().superWallpaperDepthEnabled) return
        val engineClass = android.service.wallpaper.WallpaperService.Engine::class.java
        engineClass.declaredMethods.filter { method ->
            (method.name == "getSurfaceHolder" && method.parameterCount == 0) ||
                (method.name == "updateSurface" && method.returnType == Void.TYPE &&
                method.parameterTypes.toList() in listOf(listOf(
                    Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                ), listOf(Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)))
        }.forEach { method ->
            val id = "freeform-unbounded:wallpaper-frame-sync:${methodSignature(method)}"
            if (!installedHookIds.add(id)) return@forEach
            runCatching {
                hook(method).setId(id).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(XposedInterface.Hooker { chain ->
                        val result = chain.proceed()
                        (chain.getThisObject() as? android.service.wallpaper.WallpaperService.Engine)
                            ?.let(WallpaperFrameSync::observeEngine)
                        result
                    })
            }.onFailure {
                installedHookIds.remove(id)
                log(Log.WARN, TAG, "CI92 wallpaper frame-sync observer failed", it)
            }
        }
    }

    /** Public Android API hook: retain the actual stock effect instead of guessing a private getter. */
    private fun installClockMaskEffectObserver() {
        if (!readConfig().superWallpaperDepthEnabled || android.os.Build.VERSION.SDK_INT < 33) return
        val id = "freeform-unbounded:clock-mask-effect-observer"
        if (!installedHookIds.add(id)) return
        runCatching {
            val method = android.view.View::class.java.getDeclaredMethod(
                "setRenderEffect", android.graphics.RenderEffect::class.java,
            )
            hook(method).setId(id).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(XposedInterface.Hooker { chain ->
                    val view = chain.getThisObject() as? android.view.View
                    if (view == null) chain.proceed()
                    else chain.proceed(arrayOf(ClockMaskCompositor.observeEffectWrite(
                        view, chain.getArg(0) as? android.graphics.RenderEffect,
                    )))
                })
            ClockMaskCompositor.markEffectObserverInstalled()
            log(Log.INFO, TAG, "CI77 installed public View.setRenderEffect clock-mask composition hook")
        }.onFailure {
            installedHookIds.remove(id)
            log(Log.ERROR, TAG, "CI77 clock-mask effect observer failed", it)
        }
    }

    private fun isSuperWallpaperProcess(process: String): Boolean =
        process.startsWith("$PACKAGE_MI_WALLPAPER.")

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
                    "$process:missing:${profile.className}",
                    "$process: profile class not found: ${profile.className}",
                )
                return@forEach
            } catch (error: LinkageError) {
                logLimited(
                    Log.WARN,
                    "$process:link:${profile.className}",
                    "Cannot link ${profile.className}",
                    error,
                )
                return@forEach
            }

            var matchedInClass = 0
            methodsFor(targetClass).forEach { method ->
                val rule = profile.rules.firstOrNull { it.matches(method) } ?: return@forEach
                matchedInClass++
                if (installHook(profile, method, rule)) installed++
            }
            if (matchedInClass == 0) {
                val candidates = methodsFor(targetClass)
                    .asSequence()
                    .filter(::isAdaptationCandidate)
                    .take(MAX_DIAGNOSTIC_METHODS)
                    .joinToString("; ") { methodSignature(it) }
                    .ifBlank { "none" }
                logLimited(
                    Log.WARN,
                    "$process:unmatched:${profile.className}",
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

    private fun methodsFor(type: Class<*>): Sequence<Method> = sequence {
        val seen = HashSet<String>()
        var current: Class<*>? = type
        while (current != null) {
            current.declaredMethods.forEach { method ->
                // LibXposed cannot intercept interface/abstract declarations.
                // Concrete implementations are discovered from their owning class.
                if (!Modifier.isAbstract(method.modifiers) && seen.add(methodSignature(method))) {
                    yield(method)
                }
            }
            current = current.superclass
        }
    }

    /**
     * Snow-mountain builds have moved the renderer behind different package
     * and base-class names. Inspect only the wallpaper APK's dex entries and
     * then reuse the same signature-checked engine rule as known profiles.
     */
    private fun installDiscoveredSuperWallpaperProfiles(classLoader: ClassLoader, process: String) {
        var inspected = 0
        var matched = 0
        discoverDexClassNames(classLoader)
            .filter { name ->
                name.startsWith("$PACKAGE_MI_WALLPAPER.") &&
                    DISCOVERY_NAME_PARTS.any(name.lowercase()::contains)
            }
            .distinct()
            .take(MAX_DISCOVERY_CLASSES)
            .forEach { className ->
                val targetClass = runCatching { Class.forName(className, false, classLoader) }.getOrNull()
                    ?: return@forEach
                inspected++
                val profile = HookProfiles.superWallpaperEngine(className)
                val matchedMethods = methodsFor(targetClass).filter { method ->
                    profile.rules.any { it.matches(method) }
                }.toList()
                if (matchedMethods.isNotEmpty()) {
                    matched += matchedMethods.count { method ->
                        val rule = profile.rules.first { it.matches(method) }
                        installHook(profile, method, rule)
                    }
                }
            }
        log(
            if (matched > 0) Log.INFO else Log.WARN,
            TAG,
            "$process: discovered Super wallpaper classes inspected=$inspected matchedHooks=$matched",
        )
    }

    private fun discoverDexClassNames(classLoader: ClassLoader): Sequence<String> = sequence {
        val pathList = findField(classLoader.javaClass, "pathList")?.let { field ->
            runCatching {
                field.isAccessible = true
                field.get(classLoader)
            }.getOrNull()
        } ?: return@sequence
        val elements = findField(pathList.javaClass, "dexElements")?.let { field ->
            runCatching {
                field.isAccessible = true
                field.get(pathList)
            }.getOrNull()
        } ?: return@sequence
        for (index in 0 until ReflectArray.getLength(elements)) {
            val element = ReflectArray.get(elements, index) ?: continue
            val dexFile = findField(element.javaClass, "dexFile")?.let { field ->
                runCatching {
                    field.isAccessible = true
                    field.get(element)
                }.getOrNull()
            } ?: continue
            val entries = runCatching {
                dexFile.javaClass.getMethod("entries").invoke(dexFile) as? Enumeration<*>
            }.getOrNull() ?: continue
            while (entries.hasMoreElements()) {
                val name = entries.nextElement() as? String
                if (name != null) yield(name)
            }
        }
    }

    private fun findField(type: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = type
        while (current != null) {
            current.declaredFields.firstOrNull { it.name == name }?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun installHook(
        profile: ClassHookProfile,
        method: Method,
        rule: MethodHookRule,
    ): Boolean {
        val hookId = "freeform-unbounded:${method.declaringClass.name}#${methodSignature(method)}:${rule.action}"
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

        val featureEnabled = when {
            rule.action in FREEFORM_ACTIONS -> config.freeformBoundaryEnabled
            rule.action in AOD_GLASS_ACTIONS -> config.aodGlassEnabled
            rule.action == HookAction.ENABLE_SUPER_WALLPAPER_VIDEO_DEPTH ->
                config.superWallpaperDepthEnabled
            rule.action == HookAction.ENABLE_SUPER_WALLPAPER_VIDEO_RENDER ->
                config.superWallpaperDepthEnabled
            rule.action == HookAction.PROVIDE_SUPER_WALLPAPER_DEPTH_FRAME ->
                config.superWallpaperDepthEnabled
            rule.action == HookAction.OBSERVE_AOD_CLOCK_TRANSITION ->
                config.superWallpaperDepthEnabled
            rule.action == HookAction.ADAPT_VIDEO_DEPTH_SOURCE ->
                config.superWallpaperDepthEnabled
            rule.action == HookAction.OBSERVE_VIDEO_DEPTH_RENDER_PATH ->
                config.superWallpaperDepthEnabled
            rule.action == HookAction.CAPTURE_VIDEO_DEPTH_CALLBACK ->
                config.superWallpaperDepthEnabled
            rule.action == HookAction.OBSERVE_SUPER_WALLPAPER_ENGINE ->
                config.superWallpaperDepthEnabled
            rule.action == HookAction.OBSERVE_SUPER_WALLPAPER_RUNTIME ->
                config.superWallpaperDepthEnabled
            rule.action == HookAction.OBSERVE_WALLPAPER_REGISTRATION ->
                config.superWallpaperDepthEnabled
            else -> true
        }
        if (!featureEnabled) {
            logLimited(
                Log.INFO,
                "disabled:${hookId}",
                "Skipped ${rule.action} because its module feature is disabled",
            )
            return chain.proceed()
        }

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

            HookAction.ALLOW_GLASS_ON_ANY_WALLPAPER -> {
                val effectId = chain.getArg(0) as? Int ?: return chain.proceed()
                val commonConfig = chain.getArg(1)
                val stockResult = chain.proceed()
                val stockDisabled = stockResult as? Boolean
                if (stockDisabled != null &&
                    GlassBypassPolicy.shouldOverride(effectId, stockDisabled, commonConfig)
                ) {
                    logLimited(
                        Log.INFO,
                        hookId,
                        "Enabled Glass font effect for non-static AOD wallpaper",
                    )
                    false
                } else {
                    stockResult
                }
            }

            HookAction.PRESERVE_GLASS_EFFECT -> {
                val commonConfig = chain.getArg(0)
                val requestedEffect = chain.getArg(1) as? Int ?: return chain.proceed()
                val stockResult = chain.proceed()
                if (stockResult is Int &&
                    GlassBypassPolicy.shouldPreserveGlassEffect(requestedEffect, commonConfig)
                ) {
                    logLimited(
                        Log.INFO,
                        hookId,
                        "Preserved Glass clock effect $requestedEffect for dynamic AOD wallpaper (stock=$stockResult)",
                    )
                    requestedEffect
                } else {
                    stockResult
                }
            }

            HookAction.ALLOW_GLASS_WALLPAPER_FILTER -> {
                val stringTypes = when (val argument = chain.getArg(0)) {
                    is String -> arrayOf(argument)
                    is Array<*> -> argument.mapNotNull { it as? String }.toTypedArray()
                    else -> null
                }
                val dynamicWallpaper = GlassBypassPolicy.shouldAllowGlassWallpaperFilter(stringTypes)
                val stockResult = chain.proceed()
                if (dynamicWallpaper) {
                    dynamicGlassWallpaper.set(true)
                }
                if (stockResult is Boolean &&
                    !stockResult && dynamicWallpaper
                ) {
                    logLimited(Log.INFO, hookId, "Enabled Glass wallpaper filter for dynamic AOD wallpaper")
                    true
                } else {
                    stockResult
                }
            }

            HookAction.SKIP_GLASS_FILTER_DISABLE -> {
                if (dynamicGlassWallpaper.get() == true ||
                    GlassBypassPolicy.shouldKeepGlassEnabled(chain.getThisObject())
                ) {
                    logLimited(Log.INFO, hookId, "Skipped AOD Glass filter disable for dynamic wallpaper")
                    Unit
                } else {
                    chain.proceed()
                }
            }

            HookAction.PRESERVE_GLASS_SYSTEMUI -> {
                if (method.name == "getClockBeanFromSetting") {
                    val result = chain.proceed()
                    if (GlassBypassPolicy.shouldRestorePersistedSystemUiGlass(result)) {
                        restoreSystemUiGlassBean(result, hookId, "from SystemUI settings")
                    }
                    result
                } else {
                    val clockBean = chain.getArg(2)
                    val shouldRestore = GlassBypassPolicy.shouldRestoreSystemUiGlass(
                        chain.getThisObject(),
                        clockBean,
                    )
                    if (shouldRestore) {
                        // Restore before the receiver reads the bean. MIUI can
                        // normalize the incoming effect during this call.
                        restoreSystemUiGlassBean(clockBean, hookId, "before SystemUI clock rebind")
                    }
                    val result = chain.proceed()
                    // Also restore after the call so a normalization performed
                    // inside the receiver cannot leave the applied bean at 1.
                    if (shouldRestore) {
                        restoreSystemUiGlassBean(clockBean, hookId, "after SystemUI clock rebind")
                    }
                    result
                }
            }

            HookAction.ENABLE_SUPER_WALLPAPER_VIDEO_DEPTH -> {
                val target = chain.getThisObject()
                val stockResult = chain.proceed()
                val superTarget = VideoDepthPolicy.isSuperWallpaperTarget(target)
                val subjectReady = superTarget && VideoDepthPolicy.isDepthInputReady(target)
                if (method.name == "isWallpaperSupportDepth" && subjectReady) {
                    VideoDepthPolicy.applyVideoDepth(target)
                    logLimited(
                        Log.INFO,
                        hookId,
                        "Enabled stock Super wallpaper subject-mask depth support; " +
                            "stock=$stockResult subjectMask=true",
                    )
                    true
                } else if (method.name == "updateShowDepthState" && subjectReady) {
                    // The stock setting remains 0 for Super wallpapers on this
                    // build. Enable only the already-existing SystemUI static
                    // subject-mask evaluator for this panel instance.
                    val forced = VideoDepthPolicy.forceStaticSubjectDepth(target)
                    logLimited(
                        Log.INFO,
                        hookId,
                        "Enabled SystemUI subject-mask depth state for Super wallpaper; " +
                            "stock=$stockResult subjectMask=true forced=$forced",
                    )
                    stockResult
                } else if (superTarget) {
                    logLimited(
                        Log.INFO,
                        hookId,
                        "Observed Super wallpaper depth method=${method.name}: " +
                            "kept stock=$stockResult subjectMask=$subjectReady",
                    )
                    stockResult
                } else {
                    stockResult
                }
            }

            HookAction.ENABLE_SUPER_WALLPAPER_VIDEO_RENDER -> {
                val target = chain.getThisObject()
                if (VideoDepthPolicy.isSuperWallpaperTarget(target)) {
                    VideoDepthPolicy.contextOf(target)?.let(SuperWallpaperFrameBridge::installSystemUiReceiver)
                    SuperWallpaperFrameBridge.registerDepthConsumer(target)
                }
                // CI74 只裁剪时钟，不再为了复制壁纸而强制创建视频前景 Surface。
                chain.proceed()
            }

            HookAction.PROVIDE_SUPER_WALLPAPER_DEPTH_FRAME -> {
                val target = chain.getThisObject()
                val superTarget = VideoDepthPolicy.isSuperWallpaperTarget(target)
                val result = chain.proceed()
                if (superTarget) {
                    logLimited(
                        Log.INFO,
                        hookId,
                        "Kept stock Super wallpaper depth-frame request; " +
                            "GPU Z-buffer remains diagnostic-only result=${describeDepthValue(result)}",
                    )
                }
                result
            }

            HookAction.ESTABLISH_SUPER_WALLPAPER_SESSION -> {
                val target = chain.getThisObject()
                val before = VideoDepthPolicy.sessionSnapshot(target)
                val result = chain.proceed()
                val after = if (method.name == "onSurfaceDestroyed") {
                    VideoDepthPolicy.invalidateSession(target)
                    null
                } else {
                    VideoDepthPolicy.establishSession(target, config.superWallpaperDepthEnabled)
                }
                if (after != null) {
                    logLimited(
                        Log.INFO,
                        hookId,
                        "Super wallpaper depth session generation=${after.generation} " +
                            "kind=${after.originalKind}->${after.compatibilityKind} " +
                            "previous=${before?.generation ?: 0}",
                    )
                } else {
                    logLimited(
                        Log.INFO,
                        hookId,
                        "Invalidated Super wallpaper depth session after ${method.name}; " +
                            "previous=${before?.generation ?: 0}",
                    )
                }
                result
            }

            HookAction.PREPARE_VIDEO_DEPTH_INPUT -> {
                val target = chain.getThisObject()
                val snapshot = VideoDepthPolicy.sessionSnapshot(target)
                if (snapshot?.compatibilityKind == WallpaperKind.VIDEO) {
                    logLimited(
                        Log.WARN,
                        hookId,
                        "CI17 blocked synthetic Super wallpaper video-depth preparation " +
                            "generation=${snapshot.generation}; no verified renderer depth contract",
                    )
                }
                chain.proceed()
            }

            HookAction.OBSERVE_VIDEO_DEPTH_CHAIN -> {
                val target = chain.getThisObject()
                val superTarget = VideoDepthPolicy.isSuperWallpaperTarget(target)
                if (superTarget) SuperWallpaperFrameBridge.registerDepthConsumer(target)
                val args = chain.getArgs()
                val result = chain.proceed()
                if (superTarget) {
                    val keyguardShowing = if (method.name == "updateVideoDepthVisibility") {
                        args.getOrNull(1) as? Boolean
                    } else null
                    ClockMaskCompositor.refresh(target, keyguardShowing)
                }
                result
            }

            HookAction.OBSERVE_AOD_CLOCK_TRANSITION -> {
                val args = chain.getArgs()
                val panel = args.firstOrNull()
                // DEX-verified static linkage signature is
                // (Panel, show, transitionName, mask). Arm retention before
                // stock code starts its clock/surface transition.
                ClockMaskCompositor.beginAodTransition(panel, args.getOrNull(1) as? Boolean ?: true)
                val result = chain.proceed()
                ClockMaskCompositor.refresh(panel)
                result
            }

            HookAction.OBSERVE_VIDEO_DEPTH_SOURCE -> {
                val result = chain.proceed()
                logLimited(
                    Log.INFO,
                    hookId,
                    "CI17 depth source ${method.name}${method.parameterTypes.toList()} " +
                        "result=${result?.javaClass?.name ?: "null"} identity=${result?.let(System::identityHashCode)}",
                )
                result
            }

            HookAction.OBSERVE_VIDEO_DEPTH_RENDER_PATH -> {
                val args = chain.getArgs()
                val result = chain.proceed()
                logLimited(
                    Log.INFO,
                    hookId,
                    "CI24 video-depth render path " +
                        "class=${method.declaringClass.name} method=${methodSignature(method)} " +
                        "args=${describeRenderPathArgs(args)} result=${describeDepthValue(result)}",
                )
                result
            }

            HookAction.OBSERVE_VIDEO_DEPTH_CALLBACK -> {
                val args = chain.getArgs()
                val result = chain.proceed()
                val superTarget = VideoDepthPolicy.isSuperWallpaperTarget(chain.getThisObject())
                val accepted = if (!superTarget) {
                    false
                } else if (method.name == "onGetLastDepthFrameSuccess") {
                    SuperWallpaperFrameBridge.acceptIncomingFrame(args)
                } else {
                    SuperWallpaperFrameBridge.clear()
                    false
                }
                logLimited(
                    Log.INFO,
                    hookId,
                        "CI17 depth callback ${method.name}${method.parameterTypes.toList()} " +
                        "args=${describeDepthCallbackArgs(args)} result=$result " +
                        "superTarget=$superTarget accepted=$accepted",
                )
                result
            }

            HookAction.CAPTURE_VIDEO_DEPTH_CALLBACK -> {
                val args = chain.getArgs()
                val superTarget = VideoDepthPolicy.isSuperWallpaperTarget(chain.getThisObject())
                val removed = if (
                    superTarget && (
                        method.name.contains("unregister", ignoreCase = true) ||
                            method.name.contains("remove", ignoreCase = true)
                        )
                ) {
                    SuperWallpaperFrameBridge.unregisterCallbacks(args)
                } else {
                    0
                }
                val added = if (superTarget && removed == 0) {
                    SuperWallpaperFrameBridge.registerCallbacks(args)
                } else {
                    0
                }
                val result = chain.proceed()
                logLimited(
                    Log.INFO,
                    hookId,
                    "CI17 depth callback registration ${profile.className}#${method.name} " +
                        "superTarget=$superTarget added=$added removed=$removed " +
                        "active=${SuperWallpaperFrameBridge.callbackCount()}",
                )
                result
            }

            HookAction.ADAPT_VIDEO_DEPTH_SOURCE -> {
                val result = chain.proceed()
                // A normal rendered Bitmap is not a depth map. Returning a
                // luminance/edge estimate here was the source of the wrong
                // cutouts, so this adapter is deliberately pass-through until
                // MIUI supplies an actual depth frame through its callback.
                logLimited(
                    Log.INFO,
                    hookId,
                    "CI17 depth source ${method.name} kept stock result; no synthetic Super-wallpaper depth",
                )
                result
            }

            HookAction.OBSERVE_WALLPAPER_REGISTRATION -> {
                val receiver = chain.getThisObject()
                val args = chain.getArgs()
                val mapOperation = method.name == "put" || method.name == "remove" || method.name == "clear"
                val realListenerMap = !mapOperation || isWallpaperListenerMap(receiver)
                if (!realListenerMap) {
                    return chain.proceed()
                }
                val before = describeWallpaperRegistration(receiver, method.name)
                val result = chain.proceed()
                val after = describeWallpaperRegistration(receiver, method.name)
                logLimited(
                    Log.INFO,
                    hookId,
                    "CI25 wallpaper registration class=${method.declaringClass.name} " +
                        "method=${methodSignature(method)} args=${describeRenderPathArgs(args)} " +
                        "before=$before after=$after",
                )
                result
            }

            HookAction.OBSERVE_SUPER_WALLPAPER_RUNTIME -> {
                val args = chain.getArgs()
                val receiver = chain.getThisObject()
                when (method.name) {
                    "nInit" -> {
                        val initWidth = args.getOrNull(0) as? Int ?: 0
                        val initHeight = args.getOrNull(1) as? Int ?: 0
                        NativeDepthBridge.resetReadback()
                        SuperWallpaperDepthCapture.configure(initWidth, initHeight)
                        if (readConfig().superWallpaperDepthEnabled) {
                            val traceInstalled = NativeDepthBridge.installFrameworkLoaderProbe()
                            log(
                                Log.INFO,
                                TAG,
                                "CI58 framework loader slot probe prepared before renderer init installed=$traceInstalled",
                            )
                        }
                    }
                    "nResizeViewPort" -> {
                        val resizeWidth = args.getOrNull(0) as? Int ?: 0
                        val resizeHeight = args.getOrNull(1) as? Int ?: 0
                        SuperWallpaperDepthCapture.configure(resizeWidth, resizeHeight)
                    }
                    "nSetSurface" -> {
                        NativeDepthBridge.resetReadback()
                        WallpaperFrameSync.bindSurface(args.firstOrNull { it is android.view.Surface } as? android.view.Surface)
                    }
                    else -> Unit
                }
                val renderTimestamp = args.firstOrNull() as? Long
                val result = chain.proceed()
                if (method.name == "nInit") {
                    if (readConfig().superWallpaperDepthEnabled) {
                        val traceInstalled = NativeDepthBridge.installFrameworkLoaderProbe()
                        log(
                            Log.INFO,
                            TAG,
                            "CI58 framework loader slot probe retried after renderer init installed=$traceInstalled",
                        )
                    }
                    val probeInstalled = NativeDepthBridge.install(
                        readConfig().superWallpaperDepthEnabled,
                    )
                    log(
                        Log.INFO,
                        TAG,
                        "CI46 Filament camera probe retried after renderer init installed=$probeInstalled",
                    )
                }
                val event = runtimeEventSequence.incrementAndGet()
                val conclusion = when (method.name) {
                    "nRender" -> "render-call-observed; native callback validates renderer context and attachments"
                    "doFrame" -> "render-call-only; no depth Bitmap/ByteBuffer output"
                    "nSetSurface" -> "surface-bound=${args.any { it is android.view.Surface && it.isValid }}"
                    "threadSwapBuffers" -> "native-swap-thread-notify; capture belongs to verified native callback"
                    "nInit" -> "native-init-only; no depth output contract"
                    "nDestroy", "a" -> "renderer-destroyed"
                    else -> if (method.name.startsWith("n")) "native-call-observed" else "renderer-call-observed"
                }
                logLimited(
                    Log.INFO,
                    hookId,
                    "CI17 runtime event=$event process=${currentProcessName.get().orEmpty()} " +
                        "class=${method.declaringClass.name} this=${receiver?.let(System::identityHashCode) ?: 0} " +
                        "method=${methodSignature(method)} args=${describeDepthCallbackArgs(args)} " +
                        "result=${describeDepthValue(result)} conclusion=$conclusion",
                )
                if (method.name == "nDestroy" || method.name == "a") {
                    NativeDepthBridge.resetReadback()
                    SuperWallpaperDepthCapture.reset()
                    SuperWallpaperFrameBridge.clear(VideoDepthPolicy.contextOf(null), reason = "renderer-destroyed")
                }
                result
            }

            HookAction.OBSERVE_SUPER_WALLPAPER_ENGINE -> {
                val args = chain.getArgs()
                val context = VideoDepthPolicy.contextOf(chain.getThisObject())
                val result = chain.proceed()
                if (method.name == "onSurfaceDestroyed") {
                    SuperWallpaperFrameBridge.clear(context, reason = "surface-destroyed")
                }
                val surfaceDestroyed = method.name == "onSurfaceDestroyed"
                logLimited(
                    Log.INFO,
                    hookId,
                        "CI17 super engine ${profile.className}#${method.name}${method.parameterTypes.toList()} " +
                        "args=${describeDepthCallbackArgs(args)} result=${describeDepthValue(result)} " +
                        "captured=false delivered=0 callbacks=${SuperWallpaperFrameBridge.callbackCount()} " +
                        "frame=passthrough; renderer output is not a depth frame",
                )
                result
            }
        }
    }

    private fun describeDepthCallbackArgs(args: List<Any?>): String = args.joinToString(
        prefix = "[",
        postfix = "]",
    ) { arg ->
        when (arg) {
            null -> "null"
            is android.graphics.Bitmap -> "Bitmap(${arg.width}x${arg.height},${arg.config})"
            else -> arg.toString().take(160)
        }
    }

    private fun describeRenderPathArgs(args: List<Any?>): String = args.mapIndexed { index, arg ->
        when (arg) {
            null -> "$index:null"
            is android.view.Surface ->
                "$index:Surface@${System.identityHashCode(arg)}(valid=${arg.isValid})"
            is android.graphics.Bitmap -> "$index:Bitmap(${arg.width}x${arg.height},${arg.config})"
            is android.net.Uri -> "$index:Uri($arg)"
            else -> "$index:${arg.javaClass.name}@${System.identityHashCode(arg)}($arg)"
        }
    }.joinToString(prefix = "[", postfix = "]")

    private fun describeDepthValue(value: Any?): String = when (value) {
        null -> "null"
        is android.graphics.Bitmap -> "Bitmap(${value.width}x${value.height},${value.config})"
        is java.nio.ByteBuffer -> "ByteBuffer(remaining=${value.remaining()})"
        else -> "${value.javaClass.name}@${System.identityHashCode(value)}"
    }

    private fun describeWallpaperRegistration(receiver: Any?, methodName: String): String {
        if (receiver == null) return "receiver=null"
        val className = receiver.javaClass.name
        val receiverMap = receiver as? Map<*, *>
        val backingMap = receiverMap ?: findBackingMap(receiver)
        val controller = if (className == "com.miui.miwallpaper.manager.WallpaperServiceController") {
            receiver
        } else {
            findWallpaperController(receiver.javaClass.classLoader)
        }
        val map = receiverMap ?: backingMap ?: readFieldValue(controller, "r") as? Map<*, *>
        if (map != null) {
            wallpaperListenerMap.set(map)
        }
        val entries = map?.entries?.joinToString(prefix = "[", postfix = "]") { entry ->
            "${entry.key}:${entry.value?.javaClass?.name ?: "null"}@${entry.value?.let(System::identityHashCode) ?: 0}"
        } ?: "unavailable"
        val listener = if (methodName == "put" || methodName == "remove" || methodName == "clear") {
            null
        } else {
            readFieldValue(receiver, "t")
        }
        val engineId = if (methodName == "put" || methodName == "remove" || methodName == "clear") {
            null
        } else {
            invokeNoArg(receiver, "i")
        }
        val detail = if (methodName == "setLockscreenVideoSurface") {
            "surfaces=${describeSurfaceFields(receiver)}"
        } else if (methodName == "put" || methodName == "remove" || methodName == "clear") {
            "mapOperation=$methodName"
        } else {
            "listener=${listener?.javaClass?.name ?: "null"}@${listener?.let(System::identityHashCode) ?: 0} " +
                "engineId=${engineId ?: "unavailable"}"
        }
        val backing = if (backingMap != null && backingMap !== receiver) {
            " backingMap@${System.identityHashCode(backingMap)}"
        } else {
            ""
        }
        return "receiver=$className@${System.identityHashCode(receiver)}$backing $detail " +
            "controller=${controller?.javaClass?.name ?: "null"} mapSize=${map?.size ?: -1} entries=$entries"
    }

    private fun isWallpaperListenerMap(receiver: Any?): Boolean {
        val map = (receiver as? Map<*, *>) ?: findBackingMap(receiver) ?: return false
        return wallpaperListenerMap.get() === map
    }

    private fun findBackingMap(target: Any?): Map<*, *>? {
        if (target == null) return null
        return runCatching {
            var type: Class<*>? = target.javaClass
            while (type != null) {
                for (field in type.declaredFields) {
                    if (!Map::class.java.isAssignableFrom(field.type)) continue
                    field.isAccessible = true
                    val value = field.get(target)
                    if (value is Map<*, *>) return@runCatching value
                }
                type = type.superclass
            }
            null
        }.getOrNull()
    }

    private fun findWallpaperController(loader: ClassLoader?): Any? = runCatching {
        val type = Class.forName(
            "com.miui.miwallpaper.manager.WallpaperServiceController",
            false,
            loader ?: FreeformHook::class.java.classLoader,
        )
        val getter = type.declaredMethods.firstOrNull {
            java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                it.name == "l" && it.parameterTypes.isEmpty()
        } ?: return@runCatching null
        getter.isAccessible = true
        getter.invoke(null)
    }.getOrNull()

    private fun readFieldValue(target: Any?, name: String): Any? {
        if (target == null) return null
        return runCatching {
            val field = findField(target.javaClass, name) ?: return@runCatching null
            field.isAccessible = true
            field.get(target)
        }.getOrNull()
    }

    private fun invokeNoArg(target: Any?, name: String): Any? {
        if (target == null) return null
        return runCatching {
            var type: Class<*>? = target.javaClass
            while (type != null) {
                val method = type.declaredMethods.firstOrNull {
                    it.name == name && it.parameterTypes.isEmpty()
                }
                if (method != null) {
                    method.isAccessible = true
                    return@runCatching method.invoke(target)
                }
                type = type.superclass
            }
            null
        }.getOrNull()
    }

    private fun describeSurfaceFields(target: Any?): String {
        val names = listOf("D", "E", "backgroundSurface", "foregroundSurface")
        return names.joinToString(prefix = "[", postfix = "]") { name ->
            val value = readFieldValue(target, name)
            if (value is android.view.Surface) {
                "$name=Surface@${System.identityHashCode(value)} valid=${value.isValid}"
            } else {
                "$name=${value?.javaClass?.name ?: "null"}"
            }
        }
    }

    private fun restoreSystemUiGlassBean(
        clockBean: Any?,
        hookId: String,
        suffix: String,
    ) {
        val setClockEffect = clockBean?.javaClass?.methods?.firstOrNull {
            it.name == "setClockEffect" &&
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
        }
        if (setClockEffect == null) {
            logLimited(
                Log.WARN,
                hookId,
                "ClockBean.setClockEffect(int) is unavailable in SystemUI",
            )
        } else {
            runCatching {
                setClockEffect.invoke(clockBean, 5)
            }.onSuccess {
                logLimited(
                    Log.INFO,
                    hookId,
                    "Restored Glass clock effect $suffix",
                )
            }.onFailure { error ->
                logLimited(
                    Log.WARN,
                    hookId,
                    "Failed to restore Glass clock effect $suffix",
                    error,
                )
            }
        }
    }

    private fun readConfig(): HookConfig {
        val prefs = runCatching { getRemotePreferences(ModuleConfigKeys.GROUP) }.getOrNull()
            ?: return HookConfig()
        val config = HookConfig(
            securityMarginPx = runCatching {
                normalizeSecurityMargin(prefs.getInt(
                    ModuleConfigKeys.SECURITY_MARGIN,
                    ModuleConfigKeys.DEFAULT_MARGIN,
                ))
            }.getOrDefault(ModuleConfigKeys.DEFAULT_MARGIN),
            freeformBoundaryEnabled = runCatching {
                prefs.getBoolean(ModuleConfigKeys.FREEFORM_BOUNDARY_ENABLED, false)
            }.getOrDefault(false),
            aodGlassEnabled = runCatching {
                prefs.getBoolean(ModuleConfigKeys.AOD_GLASS_ENABLED, false)
            }.getOrDefault(false),
            superWallpaperDepthEnabled = runCatching {
                prefs.getBoolean(ModuleConfigKeys.SUPER_WALLPAPER_DEPTH_ENABLED, false)
            }.getOrDefault(false),
        )
        logLimited(
            Log.INFO,
            "config-read:${currentProcessName.get()}",
            "Remote config read process=${currentProcessName.get()} " +
                "glass=${config.aodGlassEnabled} depth=${config.superWallpaperDepthEnabled}",
        )
        return config
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
        val freeformBoundaryEnabled: Boolean = false,
        val aodGlassEnabled: Boolean = false,
        val superWallpaperDepthEnabled: Boolean = false,
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
        private const val PACKAGE_AOD = "com.miui.aod"
        private const val PACKAGE_MI_WALLPAPER = "com.miui.miwallpaper"
        private const val MAX_LOGS_PER_KEY = 3
        private const val MAX_DIAGNOSTIC_METHODS = 30
        private const val MAX_DISCOVERY_CLASSES = 160
        private val DISCOVERY_NAME_PARTS = setOf(
            "superwallpaper",
            "snowmountain",
            "renderer",
            "render",
            "engine",
            "texture",
            "frame",
            "filament",
        )
        private const val ACTION_MODE_UP = 1
        private val FREEFORM_ACTIONS = setOf(
            HookAction.DISABLE_BOOLEAN,
            HookAction.FREE_HORIZONTAL_FRICTION,
            HookAction.PRESERVE_HORIZONTAL_DRAG_SESSION,
            HookAction.PRESERVE_HORIZONTAL_STABLE_OFFSET,
            HookAction.PRESERVE_HORIZONTAL_ANIM_TARGET_PARAM,
            HookAction.PRESERVE_HORIZONTAL_MOVE_FINAL_BOUNDS,
        )
        private val AOD_GLASS_ACTIONS = setOf(
            HookAction.ALLOW_GLASS_ON_ANY_WALLPAPER,
            HookAction.PRESERVE_GLASS_EFFECT,
            HookAction.ALLOW_GLASS_WALLPAPER_FILTER,
            HookAction.SKIP_GLASS_FILTER_DISABLE,
            HookAction.PRESERVE_GLASS_SYSTEMUI,
        )
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
