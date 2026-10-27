package com.freeform.unbounded

import java.nio.ShortBuffer
import kotlin.math.log2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthClusterPolicyTest {
    private fun encode(raw: Float): Short =
        ((1f + log2(raw) / 24f) * 65_535f).toInt().toShort()

    @Test
    fun separatesRecordedNonzeroSkyAndMountainWithoutDiscardingDarkGeometry() {
        // 来自 CI63 同帧 GPU 快照的实际深度范围，天空并非 clear=0。
        val sky = listOf(.001549f, .001582f, .001617f)
        val mountain = listOf(.003424f, .003594f, .004564f)
        val data = (sky + mountain).map(::encode).toShortArray()
        val source = ShortBuffer.wrap(data)
        val threshold = DepthClusterPolicy.threshold(source, data.size)
        assertNotNull(threshold)
        val mask = DepthClusterPolicy.subjectDepth(source, data.size, requireNotNull(threshold))
        for (index in sky.indices) assertEquals(0, mask[index].toInt())
        for (index in sky.size until data.size) assertTrue((mask[index].toInt() and 0xffff) > 0)
    }

    @Test
    fun rejectsFlatDepthAndContinuousDepthWithoutASeparateBackground() {
        assertNull(DepthClusterPolicy.threshold(ShortBuffer.wrap(ShortArray(100) { 20_000 }), 100))
        val ramp = ShortArray(100) { (10_000 + it * 256).toShort() }
        assertNull(DepthClusterPolicy.threshold(ShortBuffer.wrap(ramp), ramp.size))
        assertNull(DepthClusterPolicy.threshold(ShortBuffer.wrap(shortArrayOf(0, 0)), 2))
    }

    @Test
    fun aSingleOutlierDoesNotSplitAnOtherwiseContinuousSurface() {
        val values = ShortArray(101) { if (it == 100) 60_000.toShort() else 10_000 }
        assertNull(DepthClusterPolicy.threshold(ShortBuffer.wrap(values), values.size))
    }
}
