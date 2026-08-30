package com.freeform.unbounded

import org.junit.Assert.assertEquals
import org.junit.Test

class PredictiveBackProgressTest {
    @Test
    fun progressIsScaledIntoConfiguredMaximum() {
        assertEquals(0.315f, predictiveBackOffsetFraction(0.9f), 0.0001f)
        assertEquals(0.225f, predictiveBackOffsetFraction(0.9f, 0.25f), 0.0001f)
    }

    @Test
    fun configuredMaximumStaysWithinPagerSafeRange() {
        assertEquals(0.18f, predictiveBackOffsetFraction(0.9f, -1f), 0.0001f)
        assertEquals(0.45f, predictiveBackOffsetFraction(0.9f, 1f), 0.0001f)
        assertEquals(0.35f, predictiveBackOffsetFraction(1f), 0.0001f)
        assertEquals(0f, predictiveBackOffsetFraction(-0.2f), 0.0001f)
    }
}
