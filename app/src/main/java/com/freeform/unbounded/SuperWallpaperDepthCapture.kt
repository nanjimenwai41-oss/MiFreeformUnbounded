package com.freeform.unbounded

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class ForegroundDepthDirection {
    LOW_VALUES_ARE_FOREGROUND,
    HIGH_VALUES_ARE_FOREGROUND,
    NONZERO_IS_GEOMETRY,
}

/**
 * Reads the renderer's depth attachment while the Super-wallpaper GL context
 * is current. This is deliberately conservative: a flat, unavailable, or
 * invalid attachment is rejected and never enters the SystemUI depth path.
 */
internal object SuperWallpaperDepthCapture {
    private const val TAG = "FreeformUnbounded"
    // Sample infrequently while identifying the renderer's actual depth target.
    // This readback is diagnostic-only and never enables the visual depth path.
    private const val DIAGNOSTIC_READBACK_ENABLED = false
    private const val CAPTURE_EVERY_RENDER = 120
    private const val MAX_OUTPUT_DIMENSION = 512
    // 当前设备 Filament 使用逆向 Z。新 native 路径保留零背景与正几何深度，
    // 不再套用普通 Z 的固定阈值，否则同一山体会被切成碎片。
    private val foregroundDepthDirection = ForegroundDepthDirection.NONZERO_IS_GEOMETRY
    // Clock avoidance can use a slower depth refresh; the RGBA subject still
    // advances on every verified renderer sample.
    private const val DEPTH_PAIR_INTERVAL_NANOS = 100_000_000L
    private const val GL_DEPTH_COMPONENT = 0x1902
    private const val GL_UNSIGNED_SHORT = 0x1403

    private var width = 0
    private var height = 0
    private var beforeRenderCount = 0
    private var renderCount = 0
    private var bufferPixels = 0
    private var buffer: ShortBuffer? = null
    private var lastDepthPairNanos = 0L
    private var foregroundPixels = IntArray(0)
    private var foregroundBitmap: Bitmap? = null
    private var clockMaskBytes = ByteArray(0)

    data class Result(
        val published: Boolean,
        val summary: String,
    )

    /** 单通道掩码不再读取或传输壁纸颜色；只裁掉被山体遮住的时钟像素。 */
    fun publishNativeClockMask(newWidth: Int, newHeight: Int, depth: ShortBuffer, capturedNanos: Long): Result {
        val pixels = newWidth.toLong() * newHeight
        if (newWidth <= 0 || newHeight <= 0 || pixels > MAX_READBACK_PIXELS || pixels > depth.remaining()) {
            return Result(false, "mask-size-rejected")
        }
        val threshold = DepthClusterPolicy.threshold(depth, pixels.toInt())
            ?: return Result(false, "mask-no-separated-background")
        val size = ClockMaskPolicy.outputSize(newWidth, newHeight) ?: return Result(false, "mask-invalid-size")
        val count = size.width * size.height
        if (clockMaskBytes.size != count) clockMaskBytes = ByteArray(count)
        val selected = ClockMaskPolicy.writeAlphaMask(
            depth, newWidth, newHeight, threshold, size.width, size.height, clockMaskBytes,
        )
        if (!ForegroundMaskPolicy.isGeometryCoverageSafe(selected, count)) return Result(false, "mask-coverage-rejected selected=$selected/$count")
        val sent = SuperWallpaperFrameBridge.publishClockMask(size.width, size.height, clockMaskBytes, capturedNanos)
        return Result(sent, "mask=${size.width}x${size.height} selected=$selected/$count bytes=$count sent=$sent")
    }

    fun configure(newWidth: Int, newHeight: Int) {
        if (newWidth <= 0 || newHeight <= 0) return
        if (width == newWidth && height == newHeight) return
        width = newWidth
        height = newHeight
        beforeRenderCount = 0
        renderCount = 0
        buffer = null
        bufferPixels = 0
        lastDepthPairNanos = 0L
        foregroundPixels = IntArray(0)
        foregroundBitmap = null
    }

    fun reset() {
        width = 0
        height = 0
        beforeRenderCount = 0
        renderCount = 0
        buffer = null
        bufferPixels = 0
        lastDepthPairNanos = 0L
        foregroundPixels = IntArray(0)
        foregroundBitmap = null
    }

    fun describeCurrentContext(stage: String): String = runCatching {
        val depthBits = IntArray(1)
        val framebuffer = IntArray(1)
        val viewport = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_DEPTH_BITS, depthBits, 0)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, framebuffer, 0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0)
        val version = GLES20.glGetString(GLES20.GL_VERSION)?.replace("\\s+".toRegex(), " ")
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER)?.replace("\\s+".toRegex(), " ")
        "$stage depthBits=${depthBits[0]} framebuffer=${framebuffer[0]} " +
            "viewport=${viewport.joinToString(",")} version=${version ?: "null"} " +
            "renderer=${renderer ?: "null"}"
    }.getOrElse { error ->
        "$stage error=${error.javaClass.simpleName}:${error.message ?: ""}"
    }

    /** Must run on the renderer thread; before/after calls are diagnostic phases. */
    fun captureBeforeRender(context: Context?, renderTimestampNanos: Long? = null): Result =
        captureAtCurrentContext(
            context = context,
            renderTimestampNanos = renderTimestampNanos,
            frameNumber = ++beforeRenderCount,
            phase = "before",
            publish = false,
        )

    fun captureAfterRender(context: Context?, renderTimestampNanos: Long? = null): Result =
        captureAtCurrentContext(
            context = context,
            renderTimestampNanos = renderTimestampNanos,
            frameNumber = ++renderCount,
            phase = "after",
            publish = true,
        )

    /** Publishes a depth buffer captured by the native EGL swap hook. */
    fun publishNativeFrame(newWidth: Int, newHeight: Int, source: ShortBuffer): Result {
        val pixels = newWidth.toLong() * newHeight.toLong()
        if (newWidth <= 0 || newHeight <= 0 || pixels <= 0 || pixels > MAX_READBACK_PIXELS) {
            return Result(false, "native size-rejected size=${newWidth}x$newHeight pixels=$pixels")
        }
        if (source.remaining() < pixels.toInt()) {
            return Result(
                false,
                "native buffer-too-small size=${newWidth}x$newHeight " +
                    "remaining=${source.remaining()} expected=$pixels",
            )
        }
        val count = pixels.toInt()
        val stats = inspect(source, count)
        if (stats.range < MIN_VALID_RANGE || stats.nonZero == 0) {
            return Result(
                false,
                "native flat-depth size=${newWidth}x$newHeight ${stats.describe(count)}",
            )
        }
        val bitmap = toBitmap(source, newWidth, newHeight)
        val delivered = SuperWallpaperFrameBridge.publishDepthFrame(bitmap)
        return Result(
            true,
            "native published=${bitmap.width}x${bitmap.height} " +
                "${stats.describe(count)} delivered=$delivered",
        )
    }

    /**
     * Builds a transparent foreground image from the renderer's matching color
     * and depth buffers. The depth bitmap remains diagnostic; the RGBA image
     * is sent to MIUI's foreground Surface in SystemUI.
     */
    fun publishNativeColorDepthFrame(
        newWidth: Int,
        newHeight: Int,
        color: ByteBuffer,
        depth: ShortBuffer,
    ): Result {
        val pixels = newWidth.toLong() * newHeight.toLong()
        if (newWidth <= 0 || newHeight <= 0 || pixels <= 0 || pixels > MAX_READBACK_PIXELS) {
            return Result(false, "native color-depth size-rejected size=${newWidth}x$newHeight pixels=$pixels")
        }
        val expectedColorBytes = pixels * 4L
        if (color.remaining().toLong() < expectedColorBytes || depth.remaining() < pixels.toInt()) {
            return Result(
                false,
                "native color-depth buffer-too-small size=${newWidth}x$newHeight " +
                    "color=${color.remaining()} expectedColor=$expectedColorBytes " +
                    "depth=${depth.remaining()} expectedDepth=$pixels",
            )
        }
        val stats = inspect(depth, pixels.toInt())
        if (stats.range < MIN_VALID_RANGE || stats.nonZero == 0) {
            return Result(
                false,
                "native color-depth flat-depth size=${newWidth}x$newHeight ${stats.describe(pixels.toInt())}",
            )
        }
        val threshold = DepthClusterPolicy.threshold(depth, pixels.toInt())
            ?: return Result(false, "native color-depth no-separated-background")
        val subjectDepth = DepthClusterPolicy.subjectDepth(depth, pixels.toInt(), threshold)
        val foreground = toForegroundBitmap(color, subjectDepth, newWidth, newHeight, threshold)
        val totalPixels = foreground.bitmap.width * foreground.bitmap.height
        if (!ForegroundMaskPolicy.isGeometryCoverageSafe(foreground.opaquePixels, totalPixels)) {
            return Result(
                false,
                "native color-depth foreground-mask-rejected " +
                    "opaque=${foreground.opaquePixels}/$totalPixels " +
                    "maxCoverage=${ForegroundMaskPolicy.MAX_COVERAGE}",
            )
        }
        val now = System.nanoTime()
        val publishDepthPair = lastDepthPairNanos == 0L ||
            now - lastDepthPairNanos >= DEPTH_PAIR_INTERVAL_NANOS ||
            !SuperWallpaperFrameBridge.hasDepthFrame()
        val depthBitmap = if (publishDepthPair) {
            toBitmap(color, subjectDepth, newWidth, newHeight)
        } else {
            null
        }
        // The foreground subject is the visible layer and advances at the
        // renderer cadence. The expensive depth evaluator is refreshed at a
        // bounded rate and remains tied to the most recent pair sequence.
        val delivered = if (depthBitmap != null) {
            lastDepthPairNanos = now
            SuperWallpaperFrameBridge.publishDepthForegroundPair(
                depth = depthBitmap,
                foreground = foreground.bitmap,
            )
        } else {
            SuperWallpaperFrameBridge.publishForegroundFrame(foreground.bitmap)
        }
        return Result(
            true,
            "native depth=${depthBitmap?.width ?: 0}x${depthBitmap?.height ?: 0} " +
                "${stats.describe(pixels.toInt())} delivered=$delivered " +
                "foreground=${foreground.bitmap.width}x${foreground.bitmap.height} " +
                "foregroundOpaque=${foreground.opaquePixels}/$totalPixels " +
                "foregroundDelivered=${if (delivered > 0) 1 else 0} " +
                "depthPair=${depthBitmap != null}",
        )
    }

    private fun captureAtCurrentContext(
        context: Context?,
        renderTimestampNanos: Long?,
        frameNumber: Int,
        phase: String,
        publish: Boolean,
    ): Result {
        if (!DIAGNOSTIC_READBACK_ENABLED) return Result(false, "sample-skipped")
        if (width <= 0 || height <= 0) return Result(false, "not-configured")
        if (frameNumber % CAPTURE_EVERY_RENDER != 0) {
            return Result(false, "sample-skipped")
        }

        return runCatching {
            val depthBits = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_DEPTH_BITS, depthBits, 0)
            val framebuffer = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, framebuffer, 0)
            val viewport = IntArray(4)
            GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0)
            val framebufferStatus = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            val glVersion = GLES20.glGetString(GLES20.GL_VERSION)?.replace("\\s+".toRegex(), " ")
            val glRenderer = GLES20.glGetString(GLES20.GL_RENDERER)?.replace("\\s+".toRegex(), " ")
            val state = "phase=$phase frame=$frameNumber timestamp=${renderTimestampNanos ?: -1} " +
                "size=${width}x$height depthBits=${depthBits[0]} framebuffer=${framebuffer[0]} " +
                "status=0x${framebufferStatus.toString(16)} viewport=${viewport.joinToString(",")} " +
                "glVersion=${glVersion ?: "null"} glRenderer=${glRenderer ?: "null"}"
            if (depthBits[0] <= 0) {
                return@runCatching Result(false, "$state reason=no-depth-attachment")
            }

            val pixels = width * height
            if (pixels <= 0 || pixels > MAX_READBACK_PIXELS) {
                return@runCatching Result(false, "$state reason=readback-size-rejected pixels=$pixels")
            }
            if (buffer == null || bufferPixels != pixels) {
                buffer = ByteBuffer
                    .allocateDirect(pixels * Short.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
                bufferPixels = pixels
            }

            drainErrors()
            val target = requireNotNull(buffer)
            target.clear()
            GLES20.glReadPixels(
                0,
                0,
                width,
                height,
                GL_DEPTH_COMPONENT,
                GL_UNSIGNED_SHORT,
                target,
            )
            val error = GLES20.glGetError()
            if (error != GLES20.GL_NO_ERROR) {
                return@runCatching Result(false, "$state reason=glReadPixels-error=0x${error.toString(16)}")
            }

            val stats = inspect(target, pixels)
            if (stats.range < MIN_VALID_RANGE || stats.nonZero == 0) {
                return@runCatching Result(
                    false,
                    "$state reason=flat-depth ${stats.describe(pixels)}",
                )
            }

            if (!publish) {
                return@runCatching Result(true, "$state valid-depth ${stats.describe(pixels)} publish=disabled")
            }
            val bitmap = toBitmap(target, width, height)
            val delivered = SuperWallpaperFrameBridge.publishDepthFrame(bitmap, context)
            Result(
                true,
                "$state published=${bitmap.width}x${bitmap.height} ${stats.describe(pixels)} delivered=$delivered",
            )
        }.getOrElse { error ->
            Log.w(TAG, "Super wallpaper depth readback failed", error)
            Result(
                false,
                "phase=$phase frame=$frameNumber timestamp=${renderTimestampNanos ?: -1} size=${width}x$height " +
                    "reason=exception=${error.javaClass.simpleName}",
            )
        }
    }

    private data class Stats(
        val min: Int,
        val max: Int,
        val nonZero: Int,
        val mean: Int,
        val p05: Int,
        val p50: Int,
        val p95: Int,
        val checksum: Long,
    ) {
        val range: Int get() = max - min

        fun describe(pixels: Int): String =
            "min=$min max=$max range=$range nonZero=$nonZero/$pixels mean=$mean " +
                "p05=$p05 p50=$p50 p95=$p95 checksum=${checksum.toString(16)}"
    }

    private fun inspect(source: ShortBuffer, pixels: Int): Stats {
        var minValue = Int.MAX_VALUE
        var maxValue = Int.MIN_VALUE
        var nonZero = 0
        var sum = 0L
        var checksum = FNV_OFFSET_BASIS
        val histogram = IntArray(HISTOGRAM_BUCKETS)
        for (index in 0 until pixels) {
            val value = source.get(index).toInt() and 0xffff
            minValue = min(minValue, value)
            maxValue = max(maxValue, value)
            if (value != 0) nonZero++
            sum += value
            histogram[value ushr HISTOGRAM_SHIFT]++
            checksum = (checksum xor value.toLong()) * FNV_PRIME
        }
        return Stats(
            min = minValue,
            max = maxValue,
            nonZero = nonZero,
            mean = (sum / pixels.coerceAtLeast(1)).toInt(),
            p05 = percentile(histogram, pixels, 5),
            p50 = percentile(histogram, pixels, 50),
            p95 = percentile(histogram, pixels, 95),
            checksum = checksum,
        )
    }

    private fun percentile(histogram: IntArray, pixels: Int, percentile: Int): Int {
        val target = ((pixels - 1).coerceAtLeast(0) * percentile) / 100
        var cumulative = 0
        histogram.forEachIndexed { bucket, count ->
            cumulative += count
            if (cumulative > target) return bucket shl HISTOGRAM_SHIFT
        }
        return 0xffff
    }

    private fun toBitmap(source: ShortBuffer, sourceWidth: Int, sourceHeight: Int): Bitmap {
        val scale = min(
            1f,
            MAX_OUTPUT_DIMENSION.toFloat() / max(sourceWidth, sourceHeight).toFloat(),
        )
        val outputWidth = max(1, (sourceWidth * scale).roundToInt())
        val outputHeight = max(1, (sourceHeight * scale).roundToInt())
        val outputPixelCount = outputWidth * outputHeight
        val pixels = IntArray(outputPixelCount)
        for (y in 0 until outputHeight) {
            val sourceY = sourceHeight - 1 - min(sourceHeight - 1, y * sourceHeight / outputHeight)
            for (x in 0 until outputWidth) {
                val sourceX = min(sourceWidth - 1, x * sourceWidth / outputWidth)
                val depth = source.get(sourceY * sourceWidth + sourceX).toInt() and 0xffff
                val gray = 255 - ((depth * 255L) / 65535L).toInt()
                // Depth-only captures are diagnostic. They must never become a
                // full-screen opaque subject mask without a matching color frame.
                pixels[y * outputWidth + x] = gray shl 16 or (gray shl 8) or gray
            }
        }
        return Bitmap.createBitmap(pixels, outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
    }

    private fun toBitmap(
        color: ByteBuffer,
        depth: ShortBuffer,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Bitmap {
        val scale = min(
            1f,
            MAX_OUTPUT_DIMENSION.toFloat() / max(sourceWidth, sourceHeight).toFloat(),
        )
        val outputWidth = max(1, (sourceWidth * scale).roundToInt())
        val outputHeight = max(1, (sourceHeight * scale).roundToInt())
        val pixels = IntArray(outputWidth * outputHeight)
        // The renderer clears this depth attachment to zero. MIUI's depth
        // evaluator treats alpha as the subject mask, so an opaque pixel for
        // every depth sample makes the whole screen a foreground subject.
        // Convert the verified depth range into a binary subject mask. Keep
        // the grayscale depth value in RGB for diagnostics only.
        for (y in 0 until outputHeight) {
            val sourceY = sourceHeight - 1 - min(sourceHeight - 1, y * sourceHeight / outputHeight)
            for (x in 0 until outputWidth) {
                val sourceX = min(sourceWidth - 1, x * sourceWidth / outputWidth)
                val sourceIndex = sourceY * sourceWidth + sourceX
                val depthValue = depth.get(sourceIndex).toInt() and 0xffff
                val colorIndex = sourceIndex * 4
                val red = color.get(colorIndex).toInt() and 0xff
                val green = color.get(colorIndex + 1).toInt() and 0xff
                val blue = color.get(colorIndex + 2).toInt() and 0xff
                val alpha = if (ForegroundMaskPolicy.isForeground(
                        depthValue,
                        red,
                        green,
                        blue,
                        sourceY,
                        sourceHeight,
                        foregroundDepthDirection,
                    )
                ) 255 else 0
                val gray = 255 - ((depthValue * 255L) / 65535L).toInt()
                pixels[y * outputWidth + x] =
                    (alpha shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
        }
        return Bitmap.createBitmap(
            pixels,
            outputWidth,
            outputHeight,
            Bitmap.Config.ARGB_8888,
        )
    }

    private fun toForegroundBitmap(
        color: ByteBuffer,
        depth: ShortBuffer,
        sourceWidth: Int,
        sourceHeight: Int,
        depthThreshold: Int,
    ): ForegroundFrame {
        val scale = min(
            1f,
            MAX_OUTPUT_DIMENSION.toFloat() / max(sourceWidth, sourceHeight).toFloat(),
        )
        val outputWidth = max(1, (sourceWidth * scale).roundToInt())
        val outputHeight = max(1, (sourceHeight * scale).roundToInt())
        val outputPixelCount = outputWidth * outputHeight
        if (foregroundPixels.size != outputPixelCount) foregroundPixels = IntArray(outputPixelCount)
        val pixels = foregroundPixels
        var opaque = 0
        var minForegroundX = sourceWidth
        var maxForegroundX = -1
        var minForegroundY = sourceHeight
        var maxForegroundY = -1
        var nearBlackRejected = 0
        val sceneStartFromTop = (sourceHeight * SCENE_START_FRACTION).toInt()
        val sceneStartSourceY = sourceHeight - 1 - sceneStartFromTop
        for (y in 0 until outputHeight) {
            val sourceY = sourceHeight - 1 - min(sourceHeight - 1, y * sourceHeight / outputHeight)
            for (x in 0 until outputWidth) {
                val sourceX = min(sourceWidth - 1, x * sourceWidth / outputWidth)
                val sourceIndex = sourceY * sourceWidth + sourceX
                val depthValue = depth.get(sourceIndex).toInt() and 0xffff
                val colorIndex = sourceIndex * 4
                val red = color.get(colorIndex).toInt() and 0xff
                val green = color.get(colorIndex + 1).toInt() and 0xff
                val blue = color.get(colorIndex + 2).toInt() and 0xff
                // The wallpaper clears the depth buffer to zero, and during a
                // surface transition it can briefly expose a low-depth black
                // tile. Restrict the subject to the actual mountain scene and
                // reject near-black samples so those tiles never become an
                // opaque foreground overlay over the clock.
                val candidate = ForegroundMaskPolicy.isForeground(
                    depthValue,
                    red,
                    green,
                    blue,
                    sourceY,
                    sourceHeight,
                    foregroundDepthDirection,
                )
                val renderable = ForegroundMaskPolicy.isRenderableForeground(
                    candidate,
                    red,
                    green,
                    blue,
                    MIN_FOREGROUND_CHANNEL,
                )
                if (candidate && !renderable) nearBlackRejected++
                val alpha = if (renderable) {
                    opaque++
                    minForegroundX = min(minForegroundX, sourceX)
                    maxForegroundX = max(maxForegroundX, sourceX)
                    minForegroundY = min(minForegroundY, sourceY)
                    maxForegroundY = max(maxForegroundY, sourceY)
                    255
                } else {
                    0
                }
                // Clear RGB under transparent pixels. Some vendor Surface
                // consumers sample the texture before applying alpha and turn
                // uninitialised transparent RGB into black/colored seams.
                val outputRed = if (alpha == 0) 0 else red
                val outputGreen = if (alpha == 0) 0 else green
                val outputBlue = if (alpha == 0) 0 else blue
                pixels[y * outputWidth + x] =
                    (alpha shl 24) or (outputRed shl 16) or (outputGreen shl 8) or outputBlue
            }
        }
        val bitmap = foregroundBitmap?.takeIf {
            !it.isRecycled && it.width == outputWidth && it.height == outputHeight
        } ?: Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888).also {
            foregroundBitmap = it
        }
        bitmap.setPixels(pixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
        if (DepthViewDiagnostics.shouldLog("foreground-mask")) Log.i(
            TAG,
            "CI24 foreground mask opaque=$opaque/${pixels.size} " +
                "threshold=$depthThreshold direction=$foregroundDepthDirection " +
                "sceneStartSourceY=$sceneStartSourceY " +
                "nearBlackRejected=$nearBlackRejected " +
                "sourceBounds=${minForegroundX.coerceAtMost(sourceWidth - 1)}," +
                "${minForegroundY.coerceAtMost(sourceHeight - 1)}-" +
                "${maxForegroundX.coerceAtLeast(0)},${maxForegroundY.coerceAtLeast(0)}",
        )
        return ForegroundFrame(bitmap, opaque)
    }

    private data class ForegroundFrame(val bitmap: Bitmap, val opaquePixels: Int)

    private fun drainErrors() {
        repeat(4) {
            if (GLES20.glGetError() == GLES20.GL_NO_ERROR) return
        }
    }

    private const val MIN_VALID_RANGE = 512
    private const val MAX_READBACK_PIXELS = 4_000_000
    private const val SCENE_START_FRACTION = 0.27f
    private const val MIN_FOREGROUND_CHANNEL = 8
    private const val HISTOGRAM_BUCKETS = 256
    private const val HISTOGRAM_SHIFT = 8
    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 0x100000001b3L
}

internal object ForegroundMaskPolicy {
    const val MAX_COVERAGE = 0.40f
    private const val SCENE_START_FRACTION = 0.27f

    fun isForeground(
        depth: Int,
        red: Int,
        green: Int,
        blue: Int,
        sourceY: Int,
        sourceHeight: Int,
        direction: ForegroundDepthDirection,
    ): Boolean {
        if (sourceHeight <= 0 || sourceY !in 0 until sourceHeight) return false
        if (direction == ForegroundDepthDirection.NONZERO_IS_GEOMETRY) return depth > 0
        // The readback buffer uses OpenGL's bottom-left origin. Convert the
        // top-origin scene boundary before testing the sampled row.
        val sceneStartFromTop = (sourceHeight * SCENE_START_FRACTION).toInt()
        val sceneStartSourceY = sourceHeight - 1 - sceneStartFromTop
        val foregroundDepth = when (direction) {
            ForegroundDepthDirection.LOW_VALUES_ARE_FOREGROUND ->
                depth in 1..12_000
            ForegroundDepthDirection.HIGH_VALUES_ARE_FOREGROUND ->
                depth > 12_000
            ForegroundDepthDirection.NONZERO_IS_GEOMETRY -> depth > 0
        }
        return sourceY <= sceneStartSourceY && foregroundDepth && maxOf(red, green, blue) >= 8
    }

    fun isCoverageSafe(opaquePixels: Int, totalPixels: Int): Boolean =
        opaquePixels > 0 && totalPixels > 0 && opaquePixels.toFloat() / totalPixels <= MAX_COVERAGE

    // 整个几何场景可以超过旧局部山体阈值；全屏仍拒绝，防止无效深度遮住锁屏。
    fun isGeometryCoverageSafe(opaquePixels: Int, totalPixels: Int): Boolean =
        opaquePixels > 0 && totalPixels > 0 && opaquePixels.toFloat() / totalPixels < 0.95f

    fun isRenderableForeground(
        candidate: Boolean,
        red: Int,
        green: Int,
        blue: Int,
        minimumChannel: Int,
    ): Boolean = candidate && maxOf(red, green, blue) >= minimumChannel
}
