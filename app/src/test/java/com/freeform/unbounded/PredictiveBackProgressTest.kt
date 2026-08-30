package com.freeform.unbounded

import org.junit.Assert.assertEquals
import org.junit.Test

class PredictiveBackProgressTest {
    @Test
    fun progressIsClampedToConfiguredMaximum() {
        assertEquals(0.35f, clampPredictiveBackProgress(0.9f), 0.0001f)
        assertEquals(0.25f, clampPredictiveBackProgress(0.9f, 0.25f), 0.0001f)
    }

    @Test
    fun configuredMaximumStaysWithinPagerSafeRange() {
        assertEquals(0.20f, clampPredictiveBackProgress(0.9f, -1f), 0.0001f)
        assertEquals(0.50f, clampPredictiveBackProgress(0.9f, 1f), 0.0001f)
        assertEquals(0f, clampPredictiveBackProgress(-0.2f), 0.0001f)
    }
}
