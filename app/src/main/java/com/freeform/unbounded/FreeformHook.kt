package com.freeform.unbounded

import android.graphics.Rect
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/** Modern LibXposed API 102 entry point for HyperOS 3 freeform-window hooks. */
class FreeformHook : XposedModule() {
    private val installedHookIds = ConcurrentHashMap.newKeySet<String>()
    private val logCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val horizontalDragTarget = ThreadLocal<Rect?>()

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(
            Log.INFO,
            TAG,
            "Loaded in ${param.processName}; framework=$frameworkName/$frameworkVersionCode api=$apiVersion"
        )
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        installProfiles(HookProfiles.systemServer, param.classLoader, PROCESS_SYSTEM_SERVER)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != PACKAGE_SYSTEM_UI || !param.isFirstPackage) return
        installProfiles(HookProfiles.systemUi, param.classLoader, PACKAGE_SYSTEM_UI)
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
                logLimited(Log.WARN, "link:${profile.className}", "Cannot link ${profile.className}", error)
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

    private fun installHook(profile: ClassHookProfile, method: Method, rule: MethodHookRule): Boolean {
        val hookId = "freeform-unbounded:${profile.className}#${methodSignature(method)}:${rule.action}"
        if (!installedHookIds.add(hookId)) return false

        return try {
            hook(method)
                .setId(hookId)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    when (rule.action) {
                        HookAction.DISABLE_BOOLEAN -> {
                            logLimited(
                                Log.INFO,
                                hookId,
                                "Disabled ${profile.className.substringAfterLast('.')}#${method.name}"
                            )
                            false
                        }

                        HookAction.EXPAND_RECT_RESULT -> {
                            val result = chain.proceed()
                            if (result !is Rect) {
                                result
                            } else {
                                val expanded = BoundsPolicy.expand(result)
                                logLimited(
                                    Log.DEBUG,
                                    hookId,
                                    "Expanded ${profile.className.substringAfterLast('.')}#${method.name}: " +
                                        "$result -> $expanded"
                                )
                                expanded
                            }
                        }

                        HookAction.EXPAND_MOVABLE_RECT -> {
                            val result = chain.proceed()
                            if (result !is Rect) result else {
                                val expanded = BoundsPolicy.expandMovable(result)
                                logLimited(
                                    Log.INFO,
                                    hookId,
                                    "Expanded movable bounds ${methodSignature(method)}: $result -> $expanded",
                                )
                                expanded
                            }
                        }

                        HookAction.FREE_HORIZONTAL_FRICTION -> {
                            val dragTarget = chain.getArg(2) as? Rect
                            val isMini = chain.getArg(5) as? Boolean ?: false
                            val result = chain.proceed()
                            if (result !is Rect || dragTarget == null || isMini) {
                                result
                            } else {
                                val adjusted = BoundsPolicy.keepHorizontalDragTarget(result, dragTarget)
                                logLimited(
                                    Log.DEBUG,
                                    hookId,
                                    "Released horizontal friction ${methodSignature(method)}: $result -> $adjusted",
                                )
                                adjusted
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
                                val previous = horizontalDragTarget.get()
                                horizontalDragTarget.set(Rect(dragTarget))
                                try {
                                    logLimited(
                                        Log.DEBUG,
                                        hookId,
                                        "Started horizontal drag session ${methodSignature(method)}: $dragTarget",
                                    )
                                    chain.proceed()
                                } finally {
                                    if (previous == null) {
                                        horizontalDragTarget.remove()
                                    } else {
                                        horizontalDragTarget.set(previous)
                                    }
                                }
                            }
                        }

                        HookAction.PRESERVE_HORIZONTAL_STABLE_OFFSET -> {
                            val dragTarget = horizontalDragTarget.get()
                            val currentBounds = chain.getArg(0) as? Rect
                            if (dragTarget == null || currentBounds == null) {
                                chain.proceed()
                            } else {
                                val result = chain.proceed()
                                val clamped = Rect(currentBounds)
                                currentBounds.set(BoundsPolicy.keepHorizontalDragTarget(currentBounds, dragTarget))
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
                            val dragTarget = horizontalDragTarget.get()
                            val targetBounds = chain.getArg(0) as? Rect
                            if (dragTarget == null || targetBounds == null) {
                                chain.proceed()
                            } else {
                                val original = Rect(targetBounds)
                                targetBounds.set(BoundsPolicy.keepHorizontalDragTarget(targetBounds, dragTarget))
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
                                actionMode != ACTION_MODE_UP ||
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
                                val adjusted = BoundsPolicy.keepHorizontalDragTarget(result, dragTarget)
                                logLimited(
                                    Log.DEBUG,
                                    hookId,
                                    "Preserved horizontal move final bounds ${methodSignature(method)}: " +
                                        "$result -> $adjusted target=$dragTarget",
                                )
                                adjusted
                            }
                        }
                    }
                }
            log(Log.INFO, TAG, "Hooked ${profile.className}#${methodSignature(method)}")
            true
        } catch (error: Throwable) {
            installedHookIds.remove(hookId)
            logLimited(Log.ERROR, "install:$hookId", "Failed to hook $hookId", error)
            false
        }
    }

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

    private fun logLimited(priority: Int, key: String, message: String, error: Throwable? = null) {
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

    companion object {
        private const val TAG = "FreeformUnbounded"
        private const val PACKAGE_SYSTEM_UI = "com.android.systemui"
        private const val PROCESS_SYSTEM_SERVER = "android/system_server"
        private const val MAX_LOGS_PER_KEY = 3
        private const val MAX_DIAGNOSTIC_METHODS = 30
        private const val ACTION_MODE_UP = 1
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
