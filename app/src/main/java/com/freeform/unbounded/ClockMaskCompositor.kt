package com.freeform.unbounded

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SharedMemory
import android.util.Log
import android.view.Choreographer
import android.view.View
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Only removes occluded clock pixels. Wallpaper colors are never copied or redrawn. */
@android.annotation.TargetApi(33)
internal object ClockMaskCompositor {
    private const val TAG = "FreeformUnbounded"
    private const val CLOCK_CLASS = "com.android.keyguard.clock.KeyguardClockContainer"
    private const val INTERACTOR_CLASS = "com.android.keyguard.depth.KeyguardDepthInteractor"
    private const val PANEL_CLASS = "com.android.keyguard.panel.KeyguardPanelViewController"
    private const val MAX_AGE_NANOS = 150_000_000L
    private val main by lazy { Handler(Looper.getMainLooper()) }
    private val decoder = Executors.newSingleThreadExecutor { Thread(it, "FreeformClockMask").apply { isDaemon = true } }
    private val pending = AtomicReference<Input?>(null)
    private val latestDecoded = AtomicReference<Decoded?>(null)
    private val decodeRunning = AtomicBoolean(false)
    private val vsyncScheduled = AtomicBoolean(false)
    private val generation = AtomicLong(0)
    private val latestSequence = AtomicLong(0)
    private val internalEffectWrite = ThreadLocal.withInitial { false }
    private val originalEffects = WeakHashMap<View, RenderEffect?>()
    private val states = WeakHashMap<View, State>()
    private var current: Decoded? = null
    private var power: PowerManager? = null
    @Volatile private var keyguardShowing = false
    @Volatile private var aodTransition = false
    @Volatile private var superWallpaper = false
    private var installed = false
    private var lastRegistrationNanos = 0L
    private var lastAppliedSequence = 0L
    private var applied = 0L
    private var replaced = 0L
    private var maxApplyMicros = 0L
    private var maxAgeMillis = 0L
    private var lastApplyNanos = 0L
    private val applyIntervals = LongArray(180)
    private var intervalCount = 0
    @Volatile private var hasTargets = false
    @Volatile private var effectObserverInstalled = false

    private data class Input(val sequence: Long, val width: Int, val height: Int,
        val capturedNanos: Long, val memory: SharedMemory, val generation: Long)
    private data class Decoded(val sequence: Long, val bitmap: Bitmap, val capturedNanos: Long,
        val generation: Long, val decodedNanos: Long)
    private class State {
        val shader = RuntimeShader(SHADER)
        var maskEffect: RenderEffect? = null
        var appliedSequence = 0L
        var active = false
        val coordinates = FloatArray(6)
        val matrix = Matrix()
        val rootLocation = IntArray(2)
    }

    /** Called by a hook on the real public View.setRenderEffect API before clock creation. */
    fun observeEffectWrite(view: View, effect: RenderEffect?): RenderEffect? {
        if (internalEffectWrite.get() == true || !isClockContainer(view)) return effect
        originalEffects[view] = effect
        val state = states[view] ?: return effect
        return state.maskEffect?.takeIf { state.active }?.let { mask ->
            if (effect == null) mask else RenderEffect.createChainEffect(mask, effect)
        } ?: effect
    }

    fun markEffectObserverInstalled() { effectObserverInstalled = true }

    /** Set retention before the vendor method can start hiding its clock or surfaces. */
    fun beginAodTransition(target: Any?, show: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 33 || target?.javaClass?.name != PANEL_CLASS) return
        if (!VideoDepthPolicy.isSuperWallpaperTarget(target)) return
        // The linkage callback can be the first SystemUI callback after the
        // wallpaper controller is recreated. Promote the verified target
        // before checking the retention policy so that this pre-hook is useful
        // even before the normal depth-visibility callback has refreshed us.
        superWallpaper = true
        if (!ClockMaskLifecyclePolicy.beginAodTransition(show, superWallpaper, sessionEnded = false)) return
        if (!aodTransition) Log.i(TAG, "CI77 AOD clock transition entering=true (pre-hook)")
        aodTransition = true
    }

    fun install(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { install(context) }
            return
        }
        power = context.getSystemService(PowerManager::class.java)
        val now = System.nanoTime()
        if (installed || now - lastRegistrationNanos < 1_000_000_000L) return
        lastRegistrationNanos = now
        installed = ClockMaskTransport.registerSystemUiReceiver(context, ::receive)
        if (installed) Log.i(TAG, "CI77 clock-mask SharedMemory/Binder receiver registered")
    }

    /** Private names below are from the captured OS4 DEX and live view tree. */
    fun refresh(target: Any?, keyguardShowingOverride: Boolean? = null) {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (target == null) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { refresh(target, keyguardShowingOverride) }
            return
        }
        val owner = when (target.javaClass.name) {
            INTERACTOR_CLASS -> target
            PANEL_CLASS -> field(target, "keyguardDepthInteractor")
            else -> null
        } ?: return
        if (!VideoDepthPolicy.isSuperWallpaperTarget(target)) {
            clear(sessionEnded = true, reason = "wallpaper-changed")
            return
        }
        superWallpaper = true
        if (target.javaClass.name == PANEL_CLASS) {
            (field(target, "beginToAodAnim") as? Boolean)?.let { active ->
                if (active != aodTransition) {
                    Log.i(TAG, "CI77 AOD clock transition active=$active")
                    aodTransition = active
                }
            }
        }
        val monitor = field(owner, "keyguardUpdateMonitorInjector")
        val wasShowing = keyguardShowing
        val panelShowing = if (target.javaClass.name == PANEL_CLASS) {
            field(target, "keyguardShowing") as? Boolean
        } else null
        keyguardShowing = keyguardShowingOverride ?: panelShowing ?:
            (field(monitor, "mKeyguardShowing") as? Boolean ?: keyguardShowing)
        if (!keyguardShowing && !aodTransition) {
            if (wasShowing || current != null) clear(sessionEnded = true, reason = "keyguard-hidden")
            return
        }
        val injector = field(owner, "keyguardClockInjector") ?: return
        val clock = field(injector, "keyguardClockView") as? View ?: return
        install(clock.context)
        discover(clock)
        val root = clock.rootView
        val secondaryId = root.resources.getIdentifier("miui_keyguard_foreground_clock_container", "id", "com.android.systemui")
        if (secondaryId != 0) root.findViewById<View>(secondaryId)?.let(::discover)
        // 非交互先于锁屏淡出结束；AOD 交接不能以它作为移除遮罩的条件。
        scheduleVsync()
    }

    private fun discover(view: View) {
        if (states.containsKey(view)) return
        states[view] = State()
        hasTargets = true
        val weak = WeakReference(view)
        view.viewTreeObserver.addOnPreDrawListener {
            val clock = weak.get()
            val frame = current
            if (clock != null && frame != null && isActive()) {
                states[clock]?.let { state -> apply(clock, state, frame) }
            }
            true
        }
        Log.i(TAG, "CI77 clock-mask target=${view.javaClass.name} id=${view.id} size=${view.width}x${view.height}")
    }

    private fun receive(sequence: Long, width: Int, height: Int, capturedNanos: Long, memory: SharedMemory?,
        sessionEnded: Boolean) {
        if (!advanceSequence(sequence)) { memory?.close(); return }
        if (memory == null) {
            clear(sessionEnded, if (sessionEnded) "binder-session-ended" else "binder-producer-clear")
            return
        }
        // Treat each accepted mask frame as a newer lifecycle event. If a
        // previously posted clear has not run on the UI thread yet, it must not
        // erase this frame (including when the older clear ended a session).
        val frameGeneration = generation.incrementAndGet()
        val input = Input(sequence, width, height, capturedNanos, memory, frameGeneration)
        if (pending.getAndSet(input)?.also { it.memory.close() } != null) replaced++
        startDecode()
    }

    private fun advanceSequence(sequence: Long): Boolean {
        while (true) {
            val previous = latestSequence.get()
            if (sequence <= previous) return false
            if (latestSequence.compareAndSet(previous, sequence)) return true
        }
    }

    private fun startDecode() {
        if (!decodeRunning.compareAndSet(false, true)) return
        decoder.execute {
            try {
                while (true) {
                    val input = pending.getAndSet(null) ?: break
                    try {
                        if (input.generation != generation.get() || System.nanoTime() - input.capturedNanos > MAX_AGE_NANOS) continue
                        val bitmap = Bitmap.createBitmap(input.width, input.height, Bitmap.Config.ALPHA_8)
                        val mapped = input.memory.mapReadOnly()
                        try {
                            if (bitmap.rowBytes == input.width) bitmap.copyPixelsFromBuffer(mapped)
                            else {
                                val aligned = ByteBuffer.allocate(bitmap.byteCount)
                                for (y in 0 until input.height) {
                                    mapped.limit((y + 1) * input.width)
                                    aligned.position(y * bitmap.rowBytes)
                                    aligned.put(mapped)
                                }
                                aligned.rewind()
                                bitmap.copyPixelsFromBuffer(aligned)
                            }
                        } finally { SharedMemory.unmap(mapped) }
                        if (input.generation == generation.get() && input.sequence == latestSequence.get()) {
                            latestDecoded.set(Decoded(input.sequence, bitmap, input.capturedNanos, input.generation, System.nanoTime()))
                            scheduleVsync()
                        }
                    } catch (error: Exception) {
                        Log.w(TAG, "CI77 clock-mask decode failed", error)
                    } finally { input.memory.close() }
                }
            } finally {
                decodeRunning.set(false)
                if (pending.get() != null) startDecode()
            }
        }
    }

    private fun scheduleVsync() {
        if (!vsyncScheduled.compareAndSet(false, true)) return
        main.post {
            Choreographer.getInstance().postFrameCallback {
                vsyncScheduled.set(false)
                latestDecoded.getAndSet(null)?.takeIf { it.generation == generation.get() }?.let { current = it }
                val frame = current
                // producer 重建仅使待解码帧失效；最后已显示的遮罩仍可用于退出动画。
                if (frame != null && isActive()) {
                    val started = System.nanoTime()
                    var changed = false
                    states.toList().forEach { (view, state) -> changed = apply(view, state, frame) || changed }
                    if (changed && lastAppliedSequence != frame.sequence) {
                        lastAppliedSequence = frame.sequence
                        val appliedAt = System.nanoTime()
                        if (lastApplyNanos != 0L && appliedAt - lastApplyNanos < 500_000_000L) {
                            applyIntervals[intervalCount % applyIntervals.size] = appliedAt - lastApplyNanos
                            intervalCount++
                        }
                        lastApplyNanos = appliedAt
                        applied++
                        maxApplyMicros = maxOf(maxApplyMicros, (System.nanoTime() - started) / 1000)
                        maxAgeMillis = maxOf(maxAgeMillis, (System.nanoTime() - frame.capturedNanos) / 1_000_000)
                        if (applied <= 3 || applied % 180L == 0L) {
                            val intervals = applyIntervals.take(minOf(intervalCount, applyIntervals.size)).sorted()
                            val hz = if (intervals.isEmpty()) 0.0 else 1_000_000_000.0 / intervals.average()
                            val p95 = if (intervals.isEmpty()) 0.0 else intervals[((intervals.size - 1) * .95).toInt()] / 1_000_000.0
                            Log.i(TAG,
                            "CI77 clock-mask applied=$applied sequence=${frame.sequence} size=${frame.bitmap.width}x${frame.bitmap.height} " +
                                "targets=${states.size} updateHz=$hz updateP95Ms=$p95 " +
                                "maxApplyUs=$maxApplyMicros maxAgeMs=$maxAgeMillis replaced=$replaced colorSurface=false")
                        }
                    }
                } else restoreEffects()
            }
        }
    }

    private fun isActive(): Boolean = effectObserverInstalled &&
        ClockMaskLifecyclePolicy.retainMask(keyguardShowing, aodTransition, superWallpaper, sessionEnded = false)

    private fun apply(view: View, state: State, frame: Decoded): Boolean {
        if (!view.isAttachedToWindow || !view.isShown || view.width <= 0 || view.height <= 0) return false
        val root = view.rootView
        if (root.width <= 0 || root.height <= 0) return false
        state.matrix.reset()
        view.transformMatrixToGlobal(state.matrix)
        val coordinates = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)
        state.matrix.mapPoints(coordinates)
        root.getLocationOnScreen(state.rootLocation)
        val scaleX = frame.bitmap.width.toFloat() / root.width
        val scaleY = frame.bitmap.height.toFloat() / root.height
        for (i in 0..2) {
            coordinates[i * 2] = (coordinates[i * 2] - state.rootLocation[0]) * scaleX
            coordinates[i * 2 + 1] = (coordinates[i * 2 + 1] - state.rootLocation[1]) * scaleY
        }
        val changed = !coordinates.contentEquals(state.coordinates) || state.appliedSequence != frame.sequence || !state.active
        if (!changed) return false
        coordinates.copyInto(state.coordinates)
        val shader = BitmapShader(frame.bitmap, Shader.TileMode.DECAL, Shader.TileMode.DECAL)
        shader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        state.shader.setInputBuffer("subject", shader)
        state.shader.setFloatUniform("rowX", coordinates[2] - coordinates[0], coordinates[4] - coordinates[0], coordinates[0])
        state.shader.setFloatUniform("rowY", coordinates[3] - coordinates[1], coordinates[5] - coordinates[1], coordinates[1])
        val mask = RenderEffect.createRuntimeShaderEffect(state.shader, "clockContent")
        state.maskEffect = mask
        state.active = true
        state.appliedSequence = frame.sequence
        val original = originalEffects[view]
        writeEffect(view, if (original == null) mask else RenderEffect.createChainEffect(mask, original))
        return true
    }

    fun clear(sessionEnded: Boolean = false, reason: String = "producer-reset") {
        val clearGeneration = generation.incrementAndGet()
        pending.getAndSet(null)?.memory?.close()
        latestDecoded.set(null)
        if (!hasTargets) {
            if (sessionEnded && ClockMaskLifecyclePolicy.acceptClear(clearGeneration, generation.get())) {
                superWallpaper = false
                aodTransition = false
            }
            return
        }
        val applyClear = Runnable {
            if (!ClockMaskLifecyclePolicy.acceptClear(clearGeneration, generation.get())) return@Runnable
            if (reason == "keyguard-hidden" && (keyguardShowing || aodTransition)) return@Runnable
            val retain = current != null &&
                ClockMaskLifecyclePolicy.retainMask(keyguardShowing, aodTransition, superWallpaper, sessionEnded)
            Log.i(TAG, "CI77 clock-mask clear reason=$reason retained=$retain " +
                "keyguard=$keyguardShowing aodTransition=$aodTransition " +
                "interactive=${power?.isInteractive} sequence=${current?.sequence ?: 0}")
            if (retain) {
                scheduleVsync()
            } else {
                if (sessionEnded) {
                    superWallpaper = false
                    aodTransition = false
                }
                current = null
                restoreEffects()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) applyClear.run() else main.post(applyClear)
    }

    private fun restoreEffects() {
        states.toList().forEach { (view, state) ->
            if (state.active) writeEffect(view, originalEffects[view])
            state.active = false
            state.maskEffect = null
        }
    }

    private fun writeEffect(view: View, effect: RenderEffect?) {
        internalEffectWrite.set(true)
        try { view.setRenderEffect(effect) } finally { internalEffectWrite.set(false) }
    }

    private fun isClockContainer(view: View): Boolean = view.javaClass.name == CLOCK_CLASS ||
        runCatching { view.resources.getResourceEntryName(view.id) == "miui_keyguard_foreground_clock_container" }.getOrDefault(false)

    private fun field(target: Any?, name: String): Any? {
        var type = target?.javaClass ?: return null
        while (true) {
            val found = type.declaredFields.firstOrNull { it.name == name }
            if (found != null) return runCatching { found.isAccessible = true; found.get(target) }.getOrNull()
            type = type.superclass ?: return null
        }
    }

    private const val SHADER = """
        uniform shader clockContent;
        uniform shader subject;
        uniform float3 rowX;
        uniform float3 rowY;
        half4 main(float2 p) {
            float3 local = float3(p, 1.0);
            float2 maskPosition = float2(dot(rowX, local), dot(rowY, local));
            half occlusion = clamp(subject.eval(maskPosition).a, 0.0, 1.0);
            return clockContent.eval(p) * (1.0 - occlusion);
        }
    """
}
