package com.freeform.unbounded

import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

internal object XposedServiceBridge {
    @Volatile
    var service: XposedService? = null
        private set

    private val initialized = AtomicBoolean(false)
    private val listeners = CopyOnWriteArrayList<(XposedService?) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(bound: XposedService) {
                    service = bound
                    notifyListeners(bound)
                }

                override fun onServiceDied(died: XposedService) {
                    if (service === died) service = null
                    notifyListeners(null)
                }
            })
        }
    }

    fun addListener(listener: (XposedService?) -> Unit) {
        listeners += listener
        service?.let(listener)
    }

    private fun notifyListeners(bound: XposedService?) {
        mainHandler.post { listeners.forEach { it(bound) } }
    }
}
