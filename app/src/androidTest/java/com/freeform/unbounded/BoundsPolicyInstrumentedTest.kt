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

    @Test
    fun movableExpansionReleasesBottomWithoutChangingHorizontalBounds() {
        val source = Rect(12, 48, 1080, 2200)

        val expanded = BoundsPolicy.expandMovable(source)

        assertNotSame(source, expanded)
        assertEquals(Rect(12, 48, 1080, 2200), source)
        assertEquals(source.left, expanded.left)
        assertEquals(source.top, expanded.top)
        assertEquals(source.right, expanded.right)
        assertTrue(expanded.bottom > source.bottom)
        assertEquals(1_000_000, expanded.bottom)
    }

    @Test
    fun horizontalDragTargetKeepsOriginalXAndSystemY() {
        val result = Rect(40, 120, 580, 900)
        val dragTarget = Rect(-240, 48, 300, 828)

        val adjusted = BoundsPolicy.keepHorizontalDragTarget(result, dragTarget)

        assertEquals(Rect(-240, 120, 300, 900), adjusted)
    }
}
