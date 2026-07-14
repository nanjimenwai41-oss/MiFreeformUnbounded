package com.freeform.unbounded

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoundsPolicyInstrumentedTest {
    @Test
    fun expansionReturnsLargeCopyWithoutMutatingSource() {
        val source = Rect(0, 0, 2400, 1080)

        val expanded = BoundsPolicy.expand(source)

        assertNotSame(source, expanded)
        assertEquals(Rect(0, 0, 2400, 1080), source)
        assertTrue(expanded.contains(source))
        assertTrue(expanded.width() >= 200_000)
        assertTrue(expanded.height() >= 200_000)
    }
}
