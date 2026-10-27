package com.freeform.unbounded

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Boundary for a real Super-wallpaper frame path.
 *
 * This deliberately starts in pass-through mode. A frame is replaced only when
 * a processor has been installed and returns a compatible object. Static depth
 * bitmaps and cached results are never accepted as frame input.
 */
internal object SuperWallpaperFrameBridge {
    fun interface Processor {
        fun process(frame: Any): Any?
    }

    @Volatile
    private var processor: Processor? = null

    private val callbacks = Collections.synchronizedMap(IdentityHashMap<Any, Boolean>())
    private val consumers = Collections.synchronizedMap(IdentityHashMap<Any, Boolean>())
    private val lastDepthFrame = AtomicReference<Bitmap?>(null)
    private val lastForegroundFrame = AtomicReference<Bitmap?>(null)
    private val lastFrameSequence = AtomicInteger(0)
    private val lastDepthBroadcastNanos = AtomicLong(0L)
    private val lastForegroundBroadcastNanos = AtomicLong(0L)
    private val droppedDepthBroadcasts = AtomicInteger(0)
    private val droppedForegroundBroadcasts = AtomicInteger(0)
    private val foregroundRetryAttempts = AtomicInteger(0)
    private val foregroundRetryScheduled = AtomicInteger(0)
    private val consumerUpdateScheduled = AtomicInteger(0)
    private val lastReceivedDepthSequence = AtomicInteger(0)
    private val lastReceivedForegroundSequence = AtomicInteger(0)
    private val lastDepthFrameSequence = AtomicInteger(0)
    private val lastForegroundFrameSequence = AtomicInteger(0)
    private val latestQueuedDepthSequence = AtomicInteger(0)
    private val latestQueuedForegroundSequence = AtomicInteger(0)
    private val lastAppliedForegroundSequence = AtomicInteger(0)
    private val lastAppliedDepthSequence = AtomicInteger(0)
    private val lastDepthApplyUptimeMillis = AtomicLong(0L)
    private val lastConsumerNotifyUptimeMillis = AtomicLong(0L)
    private val latestFrameSentUptimeMillis = AtomicLong(0L)
    private val latestFrameDecodedUptimeMillis = AtomicLong(0L)
    private val latestFrameTimingSequence = AtomicInteger(0)
    private val frameGeneration = AtomicLong(0L)
    private val mainHandler: Handler? by lazy {
        runCatching { Handler(Looper.getMainLooper()) }.getOrNull()
    }
    private val foregroundRetryRunnable = Runnable {
        foregroundRetryScheduled.set(0)
        requestNotifyDepthConsumers()
    }
    /** URI decode is deliberately kept off SystemUI's main looper. */
    private val frameDecodeExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "FreeformDepthDecode").apply { isDaemon = true }
        }
    }
    /** SurfaceTexture buffers are independent of the SystemUI view tree. */
    private val foregroundSurfaceExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "FreeformDepthSurface").apply { isDaemon = true }
        }
    }
    private data class ForegroundUpdate(
        val consumers: List<Any>,
        val frame: Bitmap,
        val sequence: Int,
        val generation: Long,
    )
    private val pendingForegroundUpdate = AtomicReference<ForegroundUpdate?>(null)
    private val foregroundSurfaceUpdateScheduled = AtomicInteger(0)
    private val consumerUpdateRunnable = Runnable {
        consumerUpdateScheduled.set(0)
        notifyDepthConsumersNow()
    }
    @Volatile
    private var receiverInstalled = false

    fun installProcessor(value: Processor?) {
        processor = value
    }

    fun hasProcessor(): Boolean = processor != null

    fun registerCallbacks(args: List<Any?>): Int {
        var added = 0
        args.forEach { value ->
            if (value != null && isDepthCallback(value)) {
                synchronized(callbacks) {
                    if (!callbacks.containsKey(value)) {
                        callbacks[value] = true
                        added++
                    }
                }
            }
        }
        return added
    }

    fun unregisterCallbacks(args: List<Any?>): Int {
        var removed = 0
        args.forEach { value ->
            if (value != null && isDepthCallback(value)) {
                synchronized(callbacks) {
                    if (callbacks.remove(value) != null) removed++
                }
            }
        }
        return removed
    }

    fun callbackCount(): Int = synchronized(callbacks) { callbacks.size }

    fun registerDepthConsumer(target: Any?) {
        if (target == null) return
        ClockMaskCompositor.refresh(target)
        // 当前 OS4 manager 不持有 evaluator 或 Surface；真实 consumer 是 interactor。
        if (target.javaClass.name == "com.android.keyguard.wallpaper.MiuiKeyguardWallPaperManager") return
        synchronized(consumers) { consumers[target] = true }
    }

    /** Installs the SystemUI-side transport endpoint once per process. */
    fun installSystemUiReceiver(context: Context) {
        ClockMaskCompositor.install(context)
        if (receiverInstalled) return
        synchronized(this) {
            if (receiverInstalled) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    if (intent.action == ACTION_DEPTH_CLEARED) {
                        clear(broadcastClear = false)
                        return
                    }
                    if (intent.action == ACTION_FOREGROUND_FRAME) {
                        val sequence = intent.getIntExtra(EXTRA_SEQUENCE, 0)
                        enqueueFrameDecode(receiverContext, intent, sequence, foreground = true)
                        return
                    }
                    val sequence = intent.getIntExtra(EXTRA_SEQUENCE, 0)
                    enqueueFrameDecode(receiverContext, intent, sequence, foreground = false)
                }
            }
            val filter = IntentFilter(ACTION_DEPTH_FRAME)
            filter.addAction(ACTION_DEPTH_CLEARED)
            filter.addAction(ACTION_FOREGROUND_FRAME)
            runCatching {
                ContextCompat.registerReceiver(
                    context.applicationContext,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED,
                )
                receiverInstalled = true
                Log.i(TAG, "SystemUI depth-frame receiver registered")
            }.onFailure { error ->
                Log.w(TAG, "Unable to register SystemUI depth-frame receiver", error)
            }
        }
    }

    /** Publishes a depth frame to the same callback used by video wallpapers. */
    fun publishDepthFrame(frame: Bitmap, context: Context? = null): Int {
        if (frame.isRecycled || frame.width <= 0 || frame.height <= 0) return 0
        val sequence = lastFrameSequence.incrementAndGet()
        val transport = transportFrame(frame)
        lastDepthFrame.set(transport)
        lastDepthFrameSequence.set(sequence)
        lastForegroundFrame.set(null)
        lastForegroundFrameSequence.set(0)
        val snapshot = synchronized(callbacks) { callbacks.keys.toList() }
        var delivered = 0
        snapshot.forEach { callback ->
            // A generated IMiuiVideoDepthLastFrameCallback proxy marshals the
            // Bitmap through Binder. That recreates the same ~484KB transaction
            // that previously killed SystemUI. The URI broadcast is the
            // cross-process frame transport; invoke only local stubs.
            if (!isLocalCallback(callback)) {
                Log.w(TAG, "depth callback skipped remote binder class=${callback.javaClass.name}")
                return@forEach
            }
            val method = findCallbackMethod(callback) ?: return@forEach
            runCatching {
                method.isAccessible = true
                method.invoke(callback, transport, sequence)
                delivered++
            }.onFailure {
                // A dead Binder proxy must not prevent later wallpaper sessions
                // from receiving frames.
                synchronized(callbacks) { callbacks.remove(callback) }
            }
        }
        val sender = context ?: processContext()
        val broadcastSent = if (sender == null) {
            Log.w(TAG, "depth frame transport skipped sequence=$sequence reason=no-sender-context")
            false
        } else {
            sendDepthBroadcast(
                context = sender,
                frame = transport,
                sequence = sequence,
                lastSentNanos = lastDepthBroadcastNanos,
                dropped = droppedDepthBroadcasts,
            )
        }
        if (DepthViewDiagnostics.shouldLog("depth-transport")) Log.i(
            TAG,
            "depth frame transport ${if (broadcastSent) "sent" else "skipped"} " +
                "sequence=$sequence size=${transport.width}x${transport.height} callbacks=$delivered " +
                "dropped=${droppedDepthBroadcasts.get()}",
        )
        return delivered
    }

    /** Publishes a transparent color frame for the SystemUI foreground layer. */
    fun publishForegroundFrame(frame: Bitmap, context: Context? = null): Int {
        if (frame.isRecycled || frame.width <= 0 || frame.height <= 0) return 0
        val sequence = lastFrameSequence.incrementAndGet()
        val transport = transportFrame(frame)
        lastForegroundFrame.set(transport)
        lastForegroundFrameSequence.set(sequence)
        val sender = context ?: processContext()
        val sent = if (sender == null) {
            Log.w(TAG, "foreground frame transport skipped sequence=$sequence reason=no-sender-context")
            false
        } else {
            sendSharedMemoryBroadcast(
                context = sender,
                action = ACTION_FOREGROUND_FRAME,
                frame = transport,
                sequence = sequence,
                lastSentNanos = lastForegroundBroadcastNanos,
                dropped = droppedForegroundBroadcasts,
            )
        }
        if (DepthViewDiagnostics.shouldLog("foreground-transport")) Log.i(
            TAG,
            "foreground frame transport ${if (sent) "sent" else "skipped"} " +
                "sequence=$sequence size=${transport.width}x${transport.height} " +
                "dropped=${droppedForegroundBroadcasts.get()}",
        )
        return if (sent) 1 else 0
    }

    /** Publishes depth and its matching RGBA foreground as one renderer sample. */
    fun publishDepthForegroundPair(
        depth: Bitmap,
        foreground: Bitmap,
        context: Context? = null,
    ): Int {
        if (depth.isRecycled || foreground.isRecycled ||
            depth.width <= 0 || depth.height <= 0 ||
            foreground.width <= 0 || foreground.height <= 0
        ) return 0
        val sequence = lastFrameSequence.incrementAndGet()
        val depthTransport = transportFrame(depth)
        val foregroundTransport = transportFrame(foreground)
        lastDepthFrame.set(depthTransport)
        lastForegroundFrame.set(foregroundTransport)
        lastDepthFrameSequence.set(sequence)
        lastForegroundFrameSequence.set(sequence)

        val snapshot = synchronized(callbacks) { callbacks.keys.toList() }
        var delivered = 0
        snapshot.forEach { callback ->
            if (!isLocalCallback(callback)) {
                Log.w(TAG, "depth callback skipped remote binder class=${callback.javaClass.name}")
                return@forEach
            }
            val method = findCallbackMethod(callback) ?: return@forEach
            runCatching {
                method.isAccessible = true
                method.invoke(callback, depthTransport, sequence)
                delivered++
            }.onFailure {
                synchronized(callbacks) { callbacks.remove(callback) }
            }
        }

        val sender = context ?: processContext()
        val sent = sender?.let {
            sendSharedMemoryPairBroadcast(
                context = it,
                depth = depthTransport,
                foreground = foregroundTransport,
                sequence = sequence,
            )
        } ?: false
        if (sender == null) {
            Log.w(TAG, "depth/foreground pair transport skipped sequence=$sequence reason=no-sender-context")
        }
        if (sent) {
            lastDepthBroadcastNanos.set(System.nanoTime())
            lastForegroundBroadcastNanos.set(lastDepthBroadcastNanos.get())
        }
        if (DepthViewDiagnostics.shouldLog("paired-transport")) Log.i(
            TAG,
            "depth/foreground pair transport ${if (sent) "sent" else "skipped"} " +
                "sequence=$sequence depth=${depthTransport.width}x${depthTransport.height} " +
                "foreground=${foregroundTransport.width}x${foregroundTransport.height} callbacks=$delivered",
        )
        return delivered + if (sent) 1 else 0
    }

    /** Accepts a frame arriving through the SystemUI-side Binder callback hook. */
    fun acceptIncomingFrame(args: List<Any?>): Boolean {
        val frame = args.filterIsInstance<Bitmap>()
            .firstOrNull { !it.isRecycled && it.width > 0 && it.height > 0 }
            ?: return false
        lastDepthFrame.set(transportFrame(frame))
        lastDepthFrameSequence.incrementAndGet()
        lastForegroundFrame.set(null)
        lastForegroundFrameSequence.set(0)
        requestNotifyDepthConsumers()
        return true
    }

    private fun enqueueFrameDecode(
        context: Context,
        intent: Intent,
        sequence: Int,
        foreground: Boolean,
    ) {
        val receivedAtUptimeMillis = SystemClock.uptimeMillis()
        val sentAt = intent.getLongExtra(EXTRA_FRAME_UPTIME, 0L)
        if (sentAt > 0L && SystemClock.uptimeMillis() - sentAt > 250L) return
        val lastSequence = if (foreground) lastReceivedForegroundSequence else lastReceivedDepthSequence
        val latestQueued = if (foreground) latestQueuedForegroundSequence else latestQueuedDepthSequence
        if (!isNewerSequence(lastSequence, sequence)) {
            Log.w(TAG, "Ignoring stale ${if (foreground) "foreground" else "depth"} frame sequence=$sequence")
            return
        }
        latestQueued.updateAndGet { current -> maxOf(current, sequence) }
        val generation = frameGeneration.get()
        try {
            frameDecodeExecutor.execute {
                if (generation != frameGeneration.get() ||
                    latestQueued.get() != sequence ||
                    !isNewerSequence(lastSequence, sequence)
                ) return@execute
                val frame = readUriFrame(context, intent) ?: return@execute
                if (generation != frameGeneration.get() || latestQueued.get() != sequence) return@execute
                if (foreground) {
                    lastReceivedForegroundSequence.set(sequence)
                    lastForegroundFrame.set(transportFrame(frame))
                    lastForegroundFrameSequence.set(sequence)
                    latestFrameSentUptimeMillis.set(sentAt)
                    latestFrameDecodedUptimeMillis.set(SystemClock.uptimeMillis())
                    latestFrameTimingSequence.set(sequence)
                    foregroundRetryAttempts.set(0)
                    requestNotifyDepthConsumers()
                    requestForegroundSurfaceUpdate(sequence)
                    if (DepthViewDiagnostics.shouldLog("foreground-received"))
                        Log.i(
                            TAG,
                            "foreground frame received in SystemUI ${frame.width}x${frame.height} " +
                                "broadcastToDecodeMs=${SystemClock.uptimeMillis() - receivedAtUptimeMillis}",
                        )
                    return@execute
                }
                // Depth and foreground are one renderer sample. Apply both only
                // after both payloads have decoded, preventing front/back tearing.
                val pairedForeground = if (intent.hasExtra(EXTRA_PAIR_FOREGROUND_URI)) {
                    readUriFrame(
                        context,
                        intent,
                        uriExtra = EXTRA_PAIR_FOREGROUND_URI,
                        widthExtra = EXTRA_PAIR_FOREGROUND_WIDTH,
                        heightExtra = EXTRA_PAIR_FOREGROUND_HEIGHT,
                        bytesExtra = EXTRA_PAIR_FOREGROUND_BYTES,
                    )
                } else {
                    null
                }
                if (intent.hasExtra(EXTRA_PAIR_FOREGROUND_URI) && pairedForeground == null) {
                    Log.w(TAG, "Ignoring incomplete depth/foreground pair sequence=$sequence")
                    return@execute
                }
                if (generation != frameGeneration.get() || latestQueued.get() != sequence) return@execute
                lastReceivedDepthSequence.set(sequence)
                lastDepthFrame.set(transportFrame(frame))
                        pairedForeground?.let {
                            // A foreground-only sample can overtake the depth
                            // pair on the decode executor. Never regress the
                            // visible subject to that older pair.
                            if (isNewerSequence(lastReceivedForegroundSequence, sequence)) {
                                lastReceivedForegroundSequence.set(sequence)
                                lastForegroundFrame.set(transportFrame(it))
                                lastForegroundFrameSequence.set(sequence)
                            }
                        }
                if (pairedForeground == null) {
                    // A legacy depth-only frame must never reuse a snow
                    // mountain mask produced for another renderer sample.
                    lastForegroundFrame.set(null)
                    lastForegroundFrameSequence.set(0)
                }
                lastDepthFrameSequence.set(sequence)
                latestFrameSentUptimeMillis.set(sentAt)
                latestFrameDecodedUptimeMillis.set(SystemClock.uptimeMillis())
                latestFrameTimingSequence.set(sequence)
                requestNotifyDepthConsumers()
                requestForegroundSurfaceUpdate(sequence)
                if (DepthViewDiagnostics.shouldLog("paired-received")) Log.i(
                    TAG,
                    "depth frame received in SystemUI ${frame.width}x${frame.height} " +
                        "pairedForeground=${pairedForeground != null} " +
                        "broadcastToDecodeMs=${SystemClock.uptimeMillis() - receivedAtUptimeMillis}",
                )
            }
        } catch (_: RejectedExecutionException) {
            Log.w(TAG, "Ignoring frame sequence=$sequence; decode executor is unavailable")
        }
    }

    /**
     * Completes SystemUI's actual asynchronous Function callback with a frame
     * that was already verified by the renderer bridge.
     */
    fun deliverToAsyncCallback(callback: Any?, frame: Bitmap): Boolean {
        if (callback == null || frame.isRecycled || frame.width <= 0 || frame.height <= 0) {
            return false
        }
        val apply = runCatching {
            callback.javaClass.methods.firstOrNull {
                it.name == "apply" && it.parameterTypes.size == 1
            } ?: callback.javaClass.declaredMethods.firstOrNull {
                it.name == "apply" && it.parameterTypes.size == 1
            }
        }.getOrNull() ?: return false
        return runCatching {
            apply.isAccessible = true
            apply.invoke(callback, frame)
            true
        }.onFailure { error ->
            Log.w(TAG, "Unable to deliver verified depth frame to Function callback", error)
        }.getOrDefault(false)
    }

    fun hasDepthFrame(): Boolean = lastDepthFrame.get()?.let { !it.isRecycled } == true

    /** 景深求值器以 10 Hz 刷新；沿用已有判定，允许同一锚点上的较新前景帧。 */
    fun hasRenderableForegroundFrame(): Boolean = hasDepthFrame() && latestForegroundFrame() != null &&
        hasCurrentForeground(lastDepthFrameSequence.get(), lastForegroundFrameSequence.get())

    /** Returns the most recent verified frame held by this process. */
    fun latestDepthFrame(): Bitmap? = lastDepthFrame.get()?.takeUnless { it.isRecycled }

    fun latestForegroundFrame(): Bitmap? = lastForegroundFrame.get()?.takeUnless { it.isRecycled }

    fun clear(context: Context? = null, broadcastClear: Boolean = true,
        clockMaskSessionEnded: Boolean = false, reason: String = "producer-reset") {
        ClockMaskCompositor.clear(clockMaskSessionEnded, reason)
        if (broadcastClear) (context ?: processContext())?.let { ClockMaskTransport.clear(it, System.nanoTime(), clockMaskSessionEnded) }
        // Remove only the retry owned by this bridge. Clearing the whole main
        // looper would also discard unrelated SystemUI callbacks and can leave
        // the keyguard in a partially updated state.
        mainHandler?.removeCallbacks(foregroundRetryRunnable)
        foregroundRetryScheduled.set(0)
        foregroundRetryAttempts.set(0)
        frameGeneration.incrementAndGet()
        latestQueuedDepthSequence.set(0)
        latestQueuedForegroundSequence.set(0)
        val hadFrame = lastDepthFrame.getAndSet(null) != null
        val hadForeground = lastForegroundFrame.getAndSet(null) != null
        val consumerSnapshot = synchronized(consumers) { consumers.keys.toList() }
        val hadConsumers = consumerSnapshot.isNotEmpty()
        if (hadForeground) {
            consumerSnapshot.forEach(VideoDepthPolicy::clearVerifiedForegroundFrame)
        }
        val hadSequence = lastFrameSequence.getAndSet(0) != 0
        synchronized(consumers) {
            consumers.clear()
        }
        lastDepthBroadcastNanos.set(0L)
        lastForegroundBroadcastNanos.set(0L)
        lastReceivedDepthSequence.set(0)
        lastReceivedForegroundSequence.set(0)
        lastDepthFrameSequence.set(0)
        lastForegroundFrameSequence.set(0)
        lastAppliedForegroundSequence.set(0)
        lastAppliedDepthSequence.set(0)
        lastDepthApplyUptimeMillis.set(0L)
        lastConsumerNotifyUptimeMillis.set(0L)
        latestFrameSentUptimeMillis.set(0L)
        latestFrameDecodedUptimeMillis.set(0L)
        latestFrameTimingSequence.set(0)
        pendingForegroundUpdate.set(null)
        droppedDepthBroadcasts.set(0)
        droppedForegroundBroadcasts.set(0)
        // Surface teardown is called repeatedly while SystemUI is rebuilding a
        // wallpaper. Do not broadcast a synthetic clear for an already-empty
        // bridge; that clear was invalidating ordinary video wallpaper state.
        if (!broadcastClear || (!hadFrame && !hadForeground && !hadSequence && !hadConsumers)) return
        val sender = context ?: processContext()
        sender?.let {
            runCatching {
                it.sendBroadcast(Intent(ACTION_DEPTH_CLEARED).setPackage(SYSTEM_UI_PACKAGE))
            }
        }
    }

    fun processOrOriginal(args: List<Any?>, original: Any?): Any? {
        val candidate = args.firstOrNull(::isFrame) ?: return original
        val active = processor ?: return original
        val replacement = runCatching { active.process(candidate) }.getOrNull() ?: return original
        return if (isCompatible(candidate, replacement)) replacement else original
    }

    private fun isDepthCallback(value: Any): Boolean = implementsDepthCallback(value.javaClass)

    private fun isLocalCallback(value: Any): Boolean {
        if (value is Binder) return true
        val binder = runCatching {
            value.javaClass.methods.firstOrNull {
                it.name == "asBinder" && it.parameterTypes.isEmpty()
            }?.invoke(value)
        }.getOrNull()
        if (binder != null) return binder is Binder
        val name = value.javaClass.name
        return !name.contains("BinderProxy", ignoreCase = true) &&
            !name.contains("\u0024Proxy", ignoreCase = true) &&
            !name.contains("Stub\u0024Proxy", ignoreCase = true)
    }

    private fun implementsDepthCallback(type: Class<*>?): Boolean {
        if (type == null) return false
        if (type.name == DEPTH_CALLBACK || type.name.endsWith(".IMiuiVideoDepthLastFrameCallback")) {
            return true
        }
        return type.interfaces.any(::implementsDepthCallback) || implementsDepthCallback(type.superclass)
    }

    private fun findCallbackMethod(callback: Any) = runCatching {
        callback.javaClass.methods.firstOrNull {
            it.name == "onGetLastDepthFrameSuccess" &&
                it.parameterTypes.contentEquals(arrayOf(Bitmap::class.java, Int::class.javaPrimitiveType))
        } ?: callback.javaClass.declaredMethods.firstOrNull {
            it.name == "onGetLastDepthFrameSuccess" && it.parameterTypes.size == 2
        }
    }.getOrNull()

    private fun requestNotifyDepthConsumers() {
        val handler = mainHandler
        if (handler == null || Looper.myLooper() == Looper.getMainLooper()) {
            notifyDepthConsumersNow()
            return
        }
        if (!consumerUpdateScheduled.compareAndSet(0, 1)) return
        if (!handler.post(consumerUpdateRunnable)) {
            consumerUpdateScheduled.set(0)
            Log.w(TAG, "Unable to schedule verified depth consumer update")
        }
    }

    private fun notifyDepthConsumersNow() {
        val now = SystemClock.uptimeMillis()
        val previousNotify = lastConsumerNotifyUptimeMillis.get()
        if (previousNotify != 0L &&
            now - previousNotify < DEPTH_EVALUATOR_MIN_INTERVAL_MILLIS
        ) return
        lastConsumerNotifyUptimeMillis.set(now)
        val snapshot = synchronized(consumers) { consumers.keys.toList() }
        val depthSequence = lastDepthFrameSequence.get()
        val foregroundSequence = lastForegroundFrameSequence.get()
        val foreground = latestForegroundFrame().takeIf {
            hasCurrentForeground(depthSequence, foregroundSequence)
        }
        snapshot.forEach { consumer ->
            runCatching {
                if (foreground != null) VideoDepthPolicy.ensureVerifiedForegroundSurface(consumer)
                val frame = latestDepthFrame()
                // Clock avoidance does not need to be reinitialized at the
                // renderer's full cadence. Keep the live map at 10 Hz while
                // the foreground Surface continues at the capture cadence.
                val shouldApplyDepth = frame != null &&
                    depthSequence != lastAppliedDepthSequence.get() &&
                    (lastDepthApplyUptimeMillis.get() == 0L ||
                        now - lastDepthApplyUptimeMillis.get() >= DEPTH_EVALUATOR_MIN_INTERVAL_MILLIS)
                val effectEnabled = if (shouldApplyDepth) {
                    VideoDepthPolicy.applyVerifiedDepthFrame(consumer, frame!!).also { applied ->
                        if (applied) {
                            lastAppliedDepthSequence.set(depthSequence)
                            lastDepthApplyUptimeMillis.set(now)
                        }
                    }
                } else {
                    false
                }
                if (DepthViewDiagnostics.shouldLog("consumer:${consumer.javaClass.name}")) Log.i(TAG, "verified depth consumer updated " +
                    "class=${consumer.javaClass.name} effectEnabled=$effectEnabled " +
                    "foregroundQueued=${foreground != null} " +
                    "mode=live-depth-evaluator")
            }.onFailure {
                synchronized(consumers) { consumers.remove(consumer) }
            }
        }
        if (foreground != null) {
            requestForegroundSurfaceUpdate(foregroundSequence)
        } else if (depthSequence > 0 &&
            !hasCurrentForeground(depthSequence, lastForegroundFrameSequence.get())
        ) {
            snapshot.forEach(VideoDepthPolicy::clearVerifiedForegroundFrame)
        }
    }

    /**
     * Surface locking and the full-screen hardware-canvas post must not run on
     * SystemUI's main looper. Keep only the newest update so a slow producer
     * cannot build a queue of stale foreground buffers.
     */
    private fun requestForegroundSurfaceUpdate(sequence: Int) {
        val frame = latestForegroundFrame()?.takeIf {
            sequence > 0 &&
                sequence == lastForegroundFrameSequence.get() &&
                hasCurrentForeground(lastDepthFrameSequence.get(), sequence)
        } ?: return
        if (sequence == lastAppliedForegroundSequence.get()) return
        val snapshot = synchronized(consumers) { consumers.keys.toList() }
        if (snapshot.isEmpty()) return
        pendingForegroundUpdate.set(
            ForegroundUpdate(snapshot, frame, sequence, frameGeneration.get()),
        )
        if (!foregroundSurfaceUpdateScheduled.compareAndSet(0, 1)) return
        try {
            foregroundSurfaceExecutor.execute {
                try {
                    while (true) {
                        val update = pendingForegroundUpdate.getAndSet(null) ?: break
                        if (!hasCurrentForeground(lastDepthFrameSequence.get(), update.sequence) ||
                            update.sequence != lastForegroundFrameSequence.get() ||
                            update.generation != frameGeneration.get() ||
                            update.sequence == lastAppliedForegroundSequence.get()
                        ) continue
                        var applied = false
                        update.consumers.forEach { consumer ->
                            applied = VideoDepthPolicy.applyVerifiedForegroundFrame(
                                consumer,
                                update.frame,
                            ) || applied
                        }
                        if (applied) {
                            lastAppliedForegroundSequence.set(update.sequence)
                            postForegroundSurfaceCompleted(update)
                            if (latestFrameTimingSequence.get() == update.sequence &&
                                DepthViewDiagnostics.shouldLog("surface-frame-timing")
                            ) {
                                val postedAt = SystemClock.uptimeMillis()
                                val sentAt = latestFrameSentUptimeMillis.get()
                                val decodedAt = latestFrameDecodedUptimeMillis.get()
                                Log.i(
                                    TAG,
                                    "CI68 foreground frame posted sequence=${update.sequence} " +
                                        "broadcastToDecodeMs=${decodedAt - sentAt} " +
                                        "decodeToSurfaceMs=${postedAt - decodedAt} " +
                                        "broadcastToSurfaceMs=${postedAt - sentAt}",
                                )
                            }
                        } else {
                            mainHandler?.post(::scheduleForegroundRetry)
                        }
                    }
                } finally {
                    foregroundSurfaceUpdateScheduled.set(0)
                    if (pendingForegroundUpdate.get() != null &&
                        foregroundSurfaceUpdateScheduled.compareAndSet(0, 1)
                    ) {
                        requestForegroundSurfaceExecutorDrain()
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            foregroundSurfaceUpdateScheduled.set(0)
            Log.w(TAG, "Ignoring foreground frame sequence=$sequence; surface executor is unavailable")
        }
    }

    private fun requestForegroundSurfaceExecutorDrain() {
        try {
            foregroundSurfaceExecutor.execute {
                try {
                    val update = pendingForegroundUpdate.getAndSet(null) ?: return@execute
                    if (hasCurrentForeground(lastDepthFrameSequence.get(), update.sequence) &&
                        update.sequence == lastForegroundFrameSequence.get() &&
                        update.generation == frameGeneration.get() &&
                        update.sequence != lastAppliedForegroundSequence.get()
                    ) {
                        val applied = update.consumers.any {
                            VideoDepthPolicy.applyVerifiedForegroundFrame(it, update.frame)
                        }
                        if (applied) {
                            lastAppliedForegroundSequence.set(update.sequence)
                            postForegroundSurfaceCompleted(update)
                        } else mainHandler?.post(::scheduleForegroundRetry)
                    }
                } finally {
                    foregroundSurfaceUpdateScheduled.set(0)
                }
            }
        } catch (_: RejectedExecutionException) {
            foregroundSurfaceUpdateScheduled.set(0)
        }
    }

    private fun postForegroundSurfaceCompleted(update: ForegroundUpdate) {
        mainHandler?.post {
            mainHandler?.removeCallbacks(foregroundRetryRunnable)
            foregroundRetryScheduled.set(0)
            foregroundRetryAttempts.set(0)
            if (update.generation != frameGeneration.get() ||
                update.sequence > lastAppliedForegroundSequence.get()
            ) return@post
            update.consumers.forEach { consumer ->
                runCatching {
                    VideoDepthPolicy.reapplyKeyguardForegroundAfterSurfacePost(consumer)
                }.onFailure { error ->
                    Log.w(TAG, "Unable to restore OS4 keyguard foreground parent after frame post", error)
                }
            }
        }
    }

    /** Holder creation is launched from updateVideoDepthSurface's coroutine. */
    private fun scheduleForegroundRetry() {
        val handler = mainHandler ?: return
        if (!foregroundRetryScheduled.compareAndSet(0, 1)) return
        val attempt = foregroundRetryAttempts.incrementAndGet()
        if (attempt > MAX_FOREGROUND_RETRIES) {
            foregroundRetryScheduled.set(0)
            return
        }
        if (!handler.postDelayed(foregroundRetryRunnable, FOREGROUND_RETRY_DELAY_MILLIS)) {
            foregroundRetryScheduled.set(0)
        }
    }

    private fun writeBoolean(target: Any, value: Boolean, vararg names: String): Boolean {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { it.name in names }
            if (field != null && (field.type == Boolean::class.javaPrimitiveType || field.type == Boolean::class.java)) {
                field.isAccessible = true
                field.set(target, value)
                return true
            }
            type = type.superclass
        }
        return false
    }

    private fun findNoArgMethod(target: Any, name: String) = runCatching {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val method = type.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
            if (method != null) {
                method.isAccessible = true
                return@runCatching method
            }
            type = type.superclass
        }
        null
    }.getOrNull()

    private fun sendDepthBroadcast(
        context: Context,
        frame: Bitmap,
        sequence: Int,
        lastSentNanos: AtomicLong,
        dropped: AtomicInteger,
    ): Boolean = sendSharedMemoryBroadcast(
        context = context,
        action = ACTION_DEPTH_FRAME,
        frame = frame,
        sequence = sequence,
        lastSentNanos = lastSentNanos,
        dropped = dropped,
    )

    /** Writes the payload through the module provider and broadcasts only a URI. */
    private fun sendSharedMemoryBroadcast(
        context: Context,
        action: String,
        frame: Bitmap,
        sequence: Int,
        lastSentNanos: AtomicLong,
        dropped: AtomicInteger,
    ): Boolean {
        val now = System.nanoTime()
        val previous = lastSentNanos.get()
        if (previous != 0L && now - previous < MIN_BROADCAST_INTERVAL_NANOS) {
            val count = dropped.incrementAndGet()
            Log.i(
                TAG,
                "shared frame skipped action=$action sequence=$sequence reason=rate-limit " +
                    "deltaNanos=${now - previous} dropped=$count",
            )
            return false
        }
        // transportFrame() has normalized this image to ARGB_8888. Its caller
        // does not mutate the reusable foreground bitmap until this synchronous
        // write returns, so cloning it only adds a 480KB allocation per frame.
        return runCatching {
            val kind = if (action == ACTION_FOREGROUND_FRAME) "foreground" else "depth"
            val uri = frameUri(context, sequence, kind)
            DepthFramePayload.write(context, uri, frame)
            val broadcast = Intent(action).setPackage(SYSTEM_UI_PACKAGE)
            broadcast.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            broadcast.clipData = android.content.ClipData.newRawUri("depth-frame", uri)
            // Keep the URI as a String extra. The ClipData item carries the
            // granted content URI without placing a file descriptor in the
            // broadcast Parcel.
            broadcast.putExtra(EXTRA_FRAME_URI, uri.toString())
            broadcast.putExtra(EXTRA_FRAME_WIDTH, frame.width)
            broadcast.putExtra(EXTRA_FRAME_HEIGHT, frame.height)
            broadcast.putExtra(EXTRA_FRAME_BYTES, frame.byteCount)
            broadcast.putExtra(EXTRA_SEQUENCE, sequence)
            context.sendBroadcast(broadcast)
            Log.i(
                TAG,
                "shared frame sent action=$action sequence=$sequence bytes=${frame.byteCount} " +
                    "size=${frame.width}x${frame.height} uri=$uri",
            )
            lastSentNanos.set(now)
            true
        }.onFailure { error ->
            Log.w(TAG, "Unable to send URI frame action=$action sequence=$sequence", error)
        }.getOrDefault(false)
    }

    private fun sendSharedMemoryPairBroadcast(
        context: Context,
        depth: Bitmap,
        foreground: Bitmap,
        sequence: Int,
    ): Boolean {
        val now = System.nanoTime()
        val previous = maxOf(lastDepthBroadcastNanos.get(), lastForegroundBroadcastNanos.get())
        if (previous != 0L && now - previous < MIN_BROADCAST_INTERVAL_NANOS) {
            droppedDepthBroadcasts.incrementAndGet()
            droppedForegroundBroadcasts.incrementAndGet()
            if (DepthViewDiagnostics.shouldLog("paired-throttled"))
                Log.i(TAG, "shared frame pair skipped sequence=$sequence reason=rate-limit")
            return false
        }
        // The caller keeps both buffers stable until this synchronous write
        // completes; copying them here would duplicate nearly a megabyte per
        // paired sample.
        return runCatching {
            val depthUri = frameUri(context, sequence, "depth")
            val foregroundUri = frameUri(context, sequence, "foreground")
            DepthFramePayload.write(context, depthUri, depth)
            DepthFramePayload.write(context, foregroundUri, foreground)
            val broadcast = Intent(ACTION_DEPTH_FRAME).setPackage(SYSTEM_UI_PACKAGE)
                .addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING or Intent.FLAG_RECEIVER_REGISTERED_ONLY)
            broadcast.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            broadcast.clipData = android.content.ClipData.newRawUri("depth-frame", depthUri).apply {
                addItem(android.content.ClipData.Item(foregroundUri))
            }
            broadcast.putExtra(EXTRA_FRAME_URI, depthUri.toString())
            broadcast.putExtra(EXTRA_FRAME_WIDTH, depth.width)
            broadcast.putExtra(EXTRA_FRAME_HEIGHT, depth.height)
            broadcast.putExtra(EXTRA_FRAME_BYTES, depth.byteCount)
            broadcast.putExtra(EXTRA_PAIR_FOREGROUND_URI, foregroundUri.toString())
            broadcast.putExtra(EXTRA_PAIR_FOREGROUND_WIDTH, foreground.width)
            broadcast.putExtra(EXTRA_PAIR_FOREGROUND_HEIGHT, foreground.height)
            broadcast.putExtra(EXTRA_PAIR_FOREGROUND_BYTES, foreground.byteCount)
            broadcast.putExtra(EXTRA_SEQUENCE, sequence)
            broadcast.putExtra(EXTRA_FRAME_UPTIME, SystemClock.uptimeMillis())
            context.sendBroadcast(broadcast)
            lastDepthBroadcastNanos.set(now)
            lastForegroundBroadcastNanos.set(now)
            if (DepthViewDiagnostics.shouldLog("paired-uri-sent")) Log.i(
                TAG,
                "shared frame pair sent sequence=$sequence " +
                    "depthBytes=${depth.byteCount} foregroundBytes=${foreground.byteCount}",
            )
            true
        }.onFailure { error ->
            Log.w(TAG, "Unable to send URI frame pair sequence=$sequence", error)
        }.getOrDefault(false)
    }

    private fun readUriFrame(
        context: Context,
        intent: Intent,
        uriExtra: String = EXTRA_FRAME_URI,
        widthExtra: String = EXTRA_FRAME_WIDTH,
        heightExtra: String = EXTRA_FRAME_HEIGHT,
        bytesExtra: String = EXTRA_FRAME_BYTES,
    ): Bitmap? = DepthFramePayload.read(
        context = context,
        uri = intent.getStringExtra(uriExtra)?.let(android.net.Uri::parse),
        width = intent.getIntExtra(widthExtra, 0),
        height = intent.getIntExtra(heightExtra, 0),
        bytes = intent.getIntExtra(bytesExtra, 0),
        expectedKind = if (uriExtra == EXTRA_PAIR_FOREGROUND_URI ||
            intent.action == ACTION_FOREGROUND_FRAME) "foreground" else "depth",
        authority = FRAME_PROVIDER_AUTHORITY,
        maxDimension = MAX_TRANSPORT_DIMENSION,
        uriExtra = uriExtra,
        sequence = intent.getIntExtra(EXTRA_SEQUENCE, 0),
    )

    private fun transportFrame(frame: Bitmap): Bitmap {
        val scaled = if (frame.width <= MAX_TRANSPORT_DIMENSION && frame.height <= MAX_TRANSPORT_DIMENSION) {
            frame
        } else {
            val scale = MAX_TRANSPORT_DIMENSION.toFloat() / maxOf(frame.width, frame.height)
            Bitmap.createScaledBitmap(
                frame,
                (frame.width * scale).toInt().coerceAtLeast(1),
                (frame.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        }
        return if (scaled.config == Bitmap.Config.ARGB_8888) {
            scaled
        } else {
            scaled.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    internal fun isNewerSequence(last: AtomicInteger, sequence: Int): Boolean =
        sequence > 0 && sequence > last.get()

    internal fun hasMatchingForeground(depthSequence: Int, foregroundSequence: Int): Boolean =
        depthSequence > 0 && depthSequence == foregroundSequence

    internal fun hasCurrentForeground(depthSequence: Int, foregroundSequence: Int): Boolean =
        depthSequence > 0 && foregroundSequence >= depthSequence

    private fun processContext(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val method = activityThread.getDeclaredMethod("currentApplication")
        method.isAccessible = true
        (method.invoke(null) as? Context)?.applicationContext
    }.getOrNull()

    fun publishClockMask(width: Int, height: Int, alpha: ByteArray, capturedNanos: Long): Boolean {
        val context = processContext() ?: return false
        return ClockMaskTransport.send(context, System.nanoTime(), width, height, alpha, capturedNanos)
    }
    private const val DEPTH_CALLBACK = "com.miui.miwallpaper.IMiuiVideoDepthLastFrameCallback"
    private const val ACTION_DEPTH_FRAME = "com.freeform.unbounded.action.DEPTH_FRAME"
    private const val ACTION_FOREGROUND_FRAME = "com.freeform.unbounded.action.FOREGROUND_FRAME"
    private const val ACTION_DEPTH_CLEARED = "com.freeform.unbounded.action.DEPTH_CLEARED"
    private const val EXTRA_FRAME_URI = "depth_frame_uri"
    private const val EXTRA_FRAME_WIDTH = "depth_frame_width"
    private const val EXTRA_FRAME_HEIGHT = "depth_frame_height"
    private const val EXTRA_FRAME_BYTES = "depth_frame_bytes"
    private const val EXTRA_SEQUENCE = "depth_sequence"
    private const val EXTRA_PAIR_FOREGROUND_URI = "paired_foreground_uri"
    private const val EXTRA_PAIR_FOREGROUND_WIDTH = "paired_foreground_width"
    private const val EXTRA_PAIR_FOREGROUND_HEIGHT = "paired_foreground_height"
    private const val EXTRA_PAIR_FOREGROUND_BYTES = "paired_foreground_bytes"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val MAX_TRANSPORT_DIMENSION = 512
    private const val EXTRA_FRAME_UPTIME = "frame_uptime"
    private fun frameUri(context: Context, sequence: Int, kind: String): android.net.Uri =
        android.net.Uri.parse("content://$FRAME_PROVIDER_AUTHORITY/$sequence-$kind")
    private const val FRAME_PROVIDER_AUTHORITY = "com.freeform.unbounded.depth-frames"
    private const val MIN_BROADCAST_INTERVAL_NANOS = 16_666_667L
    private const val DEPTH_EVALUATOR_MIN_INTERVAL_MILLIS = 100L
    private const val FOREGROUND_RETRY_DELAY_MILLIS = 40L
    private const val MAX_FOREGROUND_RETRIES = 4
    private const val TAG = "FreeformUnbounded"

    private fun isFrame(value: Any?): Boolean = when (value) {
        is Bitmap -> !value.isRecycled && value.width > 0 && value.height > 0
        is ByteBuffer -> value.isReadOnly.not() && value.remaining() > 0
        else -> false
    }

    private fun isCompatible(original: Any, replacement: Any): Boolean = when {
        original is Bitmap && replacement is Bitmap ->
            !replacement.isRecycled && replacement.width == original.width && replacement.height == original.height
        original is ByteBuffer && replacement is ByteBuffer -> replacement.remaining() > 0
        else -> false
    }
}
