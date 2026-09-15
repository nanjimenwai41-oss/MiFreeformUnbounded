package com.freeform.unbounded

import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ModuleStatus(
    val checking: Boolean = true,
    val active: Boolean = false,
    val pendingRestart: Boolean = false,
    val scopeEnabled: Boolean = false,
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val targets: List<String> = emptyList(),
    val message: String = "正在连接 LibXposed 服务",
)

/** Event-driven API 102 status observer. No timer is needed: Activity/window
 * lifecycle transitions call [refresh] immediately, and service bind/death is
 * delivered by XposedServiceHelper. */
internal object ModuleStatusRepository {
    private val _status = MutableStateFlow(ModuleStatus())
    val status: StateFlow<ModuleStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private var attached = false

    fun attach() {
        XposedServiceBridge.init()
        if (attached) return
        attached = true
        XposedServiceBridge.addListener { service ->
            if (service == null) {
                _status.value = ModuleStatus(checking = false, message = "LibXposed 服务未连接")
            } else {
                refresh()
            }
        }
    }

    fun refresh() {
        scope.launch { refreshNow() }
    }

    fun onLifecycleEvent(event: String) {
        // Keep the event in logs/debug traces without introducing polling.
        refresh()
    }

    private suspend fun refreshNow() = refreshMutex.withLock {
        val service = XposedServiceBridge.service
        _status.value = if (service == null) {
            ModuleStatus(checking = false, message = "LibXposed 服务未连接")
        } else {
            runCatching { collectStatus(service) }.getOrElse {
                _status.value.copy(checking = false, message = "状态读取失败，请稍后重试")
            }
        }
    }

    private fun collectStatus(service: XposedService): ModuleStatus {
        val scopePackages = runCatching { service.getScope() }.getOrDefault(emptyList())
        val targets = runCatching { service.getRunningTargets() }.getOrDefault(emptyList())
        val frameworkName = runCatching { service.frameworkName }.getOrDefault("")
        val frameworkVersion = runCatching { service.frameworkVersion }.getOrDefault("")
        val scopeEnabled = REQUIRED_SCOPE.all(scopePackages::contains)
        val currentTargets = targets.filter { target ->
            target.processName in REQUIRED_TARGETS ||
                target.processName.contains("systemui", ignoreCase = true) ||
                target.processName.contains("miui.aod", ignoreCase = true)
        }
        val active = scopeEnabled && currentTargets.any { it.state == HookedTarget.State.UP_TO_DATE }
        val pendingRestart = scopeEnabled && !active
        return ModuleStatus(
            checking = false,
            active = active,
            pendingRestart = pendingRestart,
            scopeEnabled = scopeEnabled,
            frameworkName = frameworkName,
            frameworkVersion = frameworkVersion,
            targets = targets.map { "${it.processName} · ${it.state}" },
            message = when {
                !scopeEnabled -> "缺少 SystemUI、AOD 或 MiWallpaper 作用域"
                pendingRestart -> "模块已安装，重启系统或相关目标进程后生效"
                active -> "模块已由 $frameworkName 加载并运行"
                else -> "等待目标进程加载模块"
            },
        )
    }

    private val REQUIRED_SCOPE = setOf("com.android.systemui", "com.miui.aod", "com.miui.miwallpaper")
    private val REQUIRED_TARGETS = setOf("com.android.systemui", "com.miui.aod", "com.miui.miwallpaper")
}
