package com.freeform.unbounded

import java.nio.ShortBuffer

/**
 * 当前雪山场景的 Sky 是真实网格，会写入非零深度。设备成对快照证明天空和山体
 * 形成两个分离的深度簇。按本帧的空隙分离，避免把非零天空当成前景。
 */
internal object DepthClusterPolicy {
    fun threshold(values: ShortBuffer, pixels: Int): Int? {
        if (pixels <= 0 || values.remaining() < pixels) return null
        val histogram = IntArray(256)
        for (index in 0 until pixels) {
            val value = values.get(index).toInt() and 0xffff
            if (value > 0) histogram[value ushr 8]++
        }
        val total = histogram.sum()
        val minimumCluster = maxOf(1, pixels / 50)
        var below = 0
        var previous = -1
        var widestGap = 1
        var threshold: Int? = null
        histogram.forEachIndexed { bucket, count ->
            if (count > 0) {
                val gap = bucket - previous - 1
                if (previous >= 0 && gap > widestGap && below >= minimumCluster &&
                    total - below >= minimumCluster
                ) {
                    widestGap = gap
                    threshold = ((previous + bucket) * 128).coerceAtMost(65_535)
                }
                below += count
                previous = bucket
            }
        }
        return threshold
    }

    fun subjectDepth(values: ShortBuffer, pixels: Int, threshold: Int): ShortBuffer {
        val mask = ShortArray(pixels)
        for (index in mask.indices) {
            val value = values.get(index).toInt() and 0xffff
            if (value > threshold) mask[index] = value.toShort()
        }
        return ShortBuffer.wrap(mask)
    }
}
