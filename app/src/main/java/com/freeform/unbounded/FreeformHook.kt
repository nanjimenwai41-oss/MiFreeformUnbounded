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

/** Modern LibXposed API 102 entry point for HyperOS 3 freeform-window hooks. */
class FreeformHook : XposedModule() {
    private val installedHookIds = ConcurrentHashMap.newKeySet<String>()
    private val logCounts = ConcurrentHashMap<String, AtomicInteger>()

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

    companion object {
        private const val TAG = "FreeformUnbounded"
        private const val PACKAGE_SYSTEM_UI = "com.android.systemui"
        private const val PROCESS_SYSTEM_SERVER = "android/system_server"
        private const val MAX_LOGS_PER_KEY = 3
        private const val MAX_DIAGNOSTIC_METHODS = 30
        private val ADAPTATION_NAME_PARTS = listOf(
            "freeform", "pin", "constraint", "bound", "move", "accessible", "limit",
        )
    }
}
