package com.freeform.unbounded

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object ConfigRepository {
    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var localPrefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (localPrefs != null) return
        localPrefs = context.applicationContext.getSharedPreferences(
            ModuleConfigKeys.LOCAL_FILE,
            Context.MODE_PRIVATE,
        )
        _config.value = localPrefs?.readConfig() ?: AppConfig()
        XposedServiceBridge.init()
        XposedServiceBridge.addListener { service ->
            mainHandler.post { loadFrom(service) }
        }
    }

    fun setSecurityMargin(px: Int) = update {
        it.copy(
            securityMarginPx = px.coerceIn(
                ModuleConfigKeys.MIN_MARGIN,
                ModuleConfigKeys.MAX_MARGIN,
            ),
        )
    }

    fun setFreeformBoundaryEnabled(enabled: Boolean) = update {
        it.copy(freeformBoundaryEnabled = enabled)
    }

    fun setAodGlassEnabled(enabled: Boolean) = update {
        it.copy(aodGlassEnabled = enabled)
    }

    fun reset() {
        update { AppConfig() }
    }

    private fun update(transform: (AppConfig) -> AppConfig) {
        val updated = transform(_config.value)
        persist(updated)
        _config.value = updated
    }

    private fun persist(config: AppConfig) {
        localPrefs?.edit()?.let { editor ->
            config.writeTo(editor)
            editor.apply()
        }
        val service = XposedServiceBridge.service ?: return
        runCatching {
            val remote = service.getRemotePreferences(ModuleConfigKeys.GROUP)
            val editor = remote.edit()
            config.writeTo(editor)
            editor.commit()
        }
    }

    private fun loadFrom(service: XposedService?) {
        if (service != null) {
            val remote = runCatching {
                service.getRemotePreferences(ModuleConfigKeys.GROUP)
            }.getOrNull()
            if (remote != null) {
                val remoteConfig = runCatching { remote.readConfig() }.getOrNull()
                if (remoteConfig != null) {
                    _config.value = remoteConfig
                    return
                }
            }
        }
        _config.value = localPrefs?.readConfig() ?: AppConfig()
    }

}
