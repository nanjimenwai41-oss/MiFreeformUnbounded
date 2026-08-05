package com.freeform.unbounded.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FineAdjustmentTest {
    @Test
    fun fineAdjustmentUsesOneQuarterOfTheRawDragDelta() {
        assertEquals(
            105f,
            applyFineAdjustment(
                currentValue = 100f,
                rawValue = 120f,
                previousRawValue = 100f,
            ),
            0f,
        )
    }

    @Test
    fun fineAdjustmentStaysWithinMarginRange() {
        assertEquals(
            320f,
            applyFineAdjustment(
                currentValue = 318f,
                rawValue = 400f,
                previousRawValue = 0f,
            ),
            0f,
        )
    }
}
