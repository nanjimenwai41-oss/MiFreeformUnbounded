package com.freeform.unbounded

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** 在后台续期；Provider 重建或首次注册失败后不依赖锁屏回调才能恢复。 */
internal object ClockMaskEndpointRegistrar {
    private val started = AtomicBoolean(false)
    private val executor by lazy {
        Executors.newSingleThreadScheduledExecutor {
            Thread(it, "FreeformMaskEndpoint").apply { isDaemon = true }
        }
    }

    fun start(context: Context, receiver: ClockMaskTransport.Receiver) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext ?: context
        // Device regression only: reproduce a receiver starting well after the
        // wallpaper has become stationary, without rebooting the whole phone.
        val delay = EndpointRegistrationPolicy.bootstrapDelayMillis(runCatching {
            Class.forName("android.os.SystemProperties").getDeclaredMethod(
                "get", String::class.java, String::class.java,
            ).invoke(null, "debug.freeform.endpoint_delay_ms", "0") as? String
        }.getOrNull())
        if (delay > 0) Log.i("FreeformUnbounded", "CI94 endpoint startup test delayMs=$delay")
        var registered = false
        var lastAttempt = 0L
        executor.scheduleWithFixedDelay({
            val now = System.nanoTime()
            if (lastAttempt == 0L || EndpointRegistrationPolicy.shouldRegister(registered, lastAttempt, now)) {
                lastAttempt = now
                val wasRegistered = registered
                registered = runCatching {
                    ClockMaskTransport.registerSystemUiReceiver(appContext, receiver)
                }.onFailure { Log.w("FreeformUnbounded", "景深接收端续期失败，将自动重试", it) }.getOrDefault(false)
                if (wasRegistered != registered) Log.i("FreeformUnbounded",
                    "CI94 clock-mask endpoint registered=$registered")
            }
        }, delay, 1_000, TimeUnit.MILLISECONDS)
    }
}
