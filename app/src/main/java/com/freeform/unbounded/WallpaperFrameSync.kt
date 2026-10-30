package com.freeform.unbounded

import android.os.Handler
import android.os.HandlerThread
import android.os.SharedMemory
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Surface
import android.view.SurfaceControl
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.TreeMap
import java.util.function.Consumer

/** Joins the actual BLAST wallpaper buffer with the mask sampled before its swap. */
internal object WallpaperFrameSync {
    private const val TAG = "FreeformUnbounded"
    private val lock = Any()
    private val engines = mutableListOf<WeakReference<WallpaperService.Engine>>()
    private val binding = ThreadLocal.withInitial { false }
    private var rendererSurface: Surface? = null
    @Volatile private var queue: Any? = null
    private val worker by lazy { Handler(HandlerThread("FreeformFramePair").apply { start() }.looper) }
    private val frames = TreeMap<Long, Frame>()
    private var lastSent = 0L
    private var epoch = 0L
    private var acceptAfter = 0L
    private var suspendedUntil = 0L
    private var nextPropertyRead = 0L
    private var propertyEnabled = false
    private var pairs = 0L
    private data class Frame(val stamp: Long, var registered: Boolean = false, var width: Int = 0, var height: Int = 0,
        var memory: SharedMemory? = null, var transaction: SurfaceControl.Transaction? = null)

    fun observeEngine(engine: WallpaperService.Engine) {
        if (binding.get()) return
        synchronized(lock) {
            engines.removeAll { it.get() == null }
            if (engines.none { it.get() === engine }) engines += WeakReference(engine)
            bindQueue()
        }
    }

    fun bindSurface(surface: Surface?) = synchronized(lock) {
        if (rendererSurface !== surface) {
            queue = null
            ++epoch
            acceptAfter = System.nanoTime()
            failOpen("renderer surface changed")
        }
        rendererSurface = surface
        bindQueue()
    }

    // Runs only inside the injected wallpaper host. The connected OS framework
    // was inspected for this field; missing/changed contracts leave sync off.
    @android.annotation.SuppressLint("BlockedPrivateApi")
    private fun bindQueue() {
        if (binding.get()) return
        binding.set(true)
        try {
        val surface = rendererSurface ?: return
        val engine = engines.firstNotNullOfOrNull { ref ->
            ref.get()?.takeIf { it.surfaceHolder.surface === surface }
        } ?: return
        val found = runCatching {
            WallpaperService.Engine::class.java.getDeclaredField("mBlastBufferQueue")
                .apply { isAccessible = true }.get(engine)
        }.getOrNull() ?: return
        if (queue !== found) {
            queue = found
            Log.i(TAG, "CI92 frame-pair bound actual Engine holder and BLAST queue")
        }
        } finally { binding.set(false) }
    }

    private fun enabled(): Boolean = synchronized(lock) {
        val now = System.nanoTime()
        if (now > nextPropertyRead) {
            propertyEnabled = runCatching {
                Class.forName("android.os.SystemProperties").getDeclaredMethod(
                    "get", String::class.java, String::class.java,
                ).invoke(null, "debug.freeform.frame_sync", "0") == "1"
            }.getOrDefault(false)
            nextPropertyRead = now + 1_000_000_000L
        }
        propertyEnabled && queue != null && now >= suspendedUntil
    }

    /** Called immediately before the verified renderer's real eglSwapBuffers. */
    fun beforeSwap(capturedNanos: Long) {
        if (!enabled()) return
        val target = queue ?: return
        val stamp = if (capturedNanos > 0) capturedNanos else System.nanoTime()
        val frameEpoch = synchronized(lock) {
            if (stamp <= maxOf(lastSent, acceptAfter)) return
            frames.getOrPut(stamp) { Frame(stamp) }.registered = true
            epoch
        }
        val accepted = runCatching {
            target.javaClass.getDeclaredMethod("syncNextTransaction", Consumer::class.java)
                .invoke(target, Consumer<SurfaceControl.Transaction> { transaction ->
                    worker.post {
                        synchronized(lock) {
                            if (queue !== target || frameEpoch != epoch || System.nanoTime() < suspendedUntil) {
                                try { transaction.apply() } finally { transaction.close() }
                                return@post
                            }
                            // Never recreate an entry already superseded by a newer pair.
                            // dispatchReady cannot pass a registered entry without its callback.
                            val frame = frames[stamp]
                            if (frame == null || stamp <= lastSent) {
                                Log.e(TAG, "CI92 unexpected late BLAST callback captured=$stamp last=$lastSent")
                                try { transaction.apply() } finally { transaction.close() }
                                return@post
                            }
                            frame.transaction = transaction
                            dispatchReady()
                        }
                    }
                }) == true
        }.getOrDefault(false)
        if (!accepted) {
            worker.post { synchronized(lock) { failOpen("BLAST sync rejected") } }
        } else worker.postDelayed({
            synchronized(lock) {
                if (frameEpoch == epoch && frames.containsKey(stamp)) failOpen("pair deadline exceeded")
            }
        }, 150)
    }

    /** Takes ownership of an immutable copy before the JNI direct buffer expires. */
    fun offerMask(width: Int, height: Int, stamp: Long, alpha: ByteBuffer): Boolean {
        if (!enabled()) return false
        val memory = runCatching { ClockMaskTransport.createMemory(alpha) }.getOrElse {
            worker.post { synchronized(lock) { failOpen("mask allocation failed") } }
            return false
        }
        synchronized(lock) {
            if (stamp <= maxOf(lastSent, acceptAfter)) { memory.close(); return true }
            val frame = frames.getOrPut(stamp) { Frame(stamp) }
            frame.width = width
            frame.height = height
            frame.memory?.close()
            frame.memory = memory
        }
        worker.post { synchronized(lock) { dispatchReady() } }
        return true
    }

    /** Merge in timestamp order so superseded buffers release and the newest wins. */
    private fun dispatchReady() {
        // Only a complete prefix may be sent: a callback arriving late must never
        // apply an old wallpaper buffer after a newer mask has reached SystemUI.
        val readyStamp = FramePairOrderPolicy.newestCompletePrefix(frames.values.map {
            FramePairOrderPolicy.Readiness(it.stamp, it.registered, it.transaction != null, it.memory != null)
        }, lastSent) ?: return
        val ready = checkNotNull(frames[readyStamp])
        var combined: SurfaceControl.Transaction? = null
        val iterator = frames.entries.iterator()
        while (iterator.hasNext()) {
            val frame = iterator.next().value
            if (frame.stamp > ready.stamp) break
            val transaction = checkNotNull(frame.transaction)
            if (combined == null) combined = transaction else {
                combined.merge(transaction)
                transaction.close()
            }
            if (frame !== ready) frame.memory?.close()
            iterator.remove()
        }
        val transaction = combined ?: return
        val memory = ready.memory ?: return
        try {
            val sent = SuperWallpaperFrameBridge.publishClockMask(
                ready.width, ready.height, memory, ready.stamp, transaction,
            )
            if (!sent) {
                transaction.apply()
                failOpen("paired Binder send failed")
            } else {
                lastSent = ready.stamp
                if (++pairs <= 3 || pairs % 180L == 0L) {
                    Log.i(TAG, "CI92 wallpaper/mask pair=$pairs captured=${ready.stamp}")
                }
            }
        } finally { memory.close(); transaction.close() }
    }

    private fun failOpen(reason: String) {
        Log.w(TAG, "CI92 frame-pair suspended: $reason")
        suspendedUntil = System.nanoTime() + 1_000_000_000L
        var combined: SurfaceControl.Transaction? = null
        frames.values.forEach { frame ->
            frame.memory?.close()
            frame.transaction?.let { transaction ->
                if (combined == null) combined = transaction else {
                    combined.merge(transaction)
                    transaction.close()
                }
            }
        }
        frames.clear()
        combined?.let { try { it.apply() } finally { it.close() } }
    }
}
