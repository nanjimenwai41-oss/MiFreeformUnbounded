package com.freeform.unbounded

import java.nio.ShortBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Converts the verified mountain depth cluster into a compact top-origin alpha mask. */
internal object ClockMaskPolicy {
    data class Size(val width: Int, val height: Int)

    const val MAX_DIMENSION = 1024

    fun outputSize(sourceWidth: Int, sourceHeight: Int, maxDimension: Int = MAX_DIMENSION): Size? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || maxDimension <= 0) return null
        val scale = min(1f, maxDimension.toFloat() / max(sourceWidth, sourceHeight))
        return Size(
            max(1, (sourceWidth * scale).roundToInt()),
            max(1, (sourceHeight * scale).roundToInt()),
        )
    }

    /** Returns selected pixels; the caller owns and may reuse [output]. */
    fun writeAlphaMask(
        depth: ShortBuffer,
        sourceWidth: Int,
        sourceHeight: Int,
        threshold: Int,
        outputWidth: Int,
        outputHeight: Int,
        output: ByteArray,
    ): Int {
        val sourcePixels = sourceWidth.toLong() * sourceHeight
        val outputPixels = outputWidth.toLong() * outputHeight
        require(sourceWidth > 0 && sourceHeight > 0 && outputWidth > 0 && outputHeight > 0)
        require(sourcePixels <= depth.limit() && outputPixels <= output.size)
        var selected = 0
        for (y in 0 until outputHeight) {
            // GL readback is bottom-origin; the clock mask bitmap is top-origin.
            val sourceY = sourceHeight - 1 - min(sourceHeight - 1, y * sourceHeight / outputHeight)
            for (x in 0 until outputWidth) {
                val sourceX = min(sourceWidth - 1, x * sourceWidth / outputWidth)
                val foreground = (depth.get(sourceY * sourceWidth + sourceX).toInt() and 0xffff) > threshold
                output[y * outputWidth + x] = if (foreground) 0xff.toByte() else 0
                if (foreground) selected++
            }
        }
        return selected
    }
}
