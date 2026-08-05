package com.freeform.unbounded

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BoundsPolicyInstrumentedTest {
    @Test
    fun horizontalDragTargetKeepsRequestedXAndSystemY() {
        val result = Rect(40, 120, 580, 900)
        val dragTarget = Rect(-240, 48, 300, 828)

        val adjusted = BoundsPolicy.keepHorizontalDragTarget(result, dragTarget)

        assertEquals(Rect(-240, 120, 300, 900), adjusted)
    }

    @Test
    fun requestedDragTargetRestoresBothAxesAfterPlatformClamp() {
        val result = Rect(-549, 1224, 607, 3073)
        val dragTarget = Rect(-549, 2200, 607, 4049)

        assertEquals(
            Rect(-549, 2200, 607, 4049),
            BoundsPolicy.keepRequestedDragTarget(result, dragTarget),
        )
    }

    @Test
    fun edgeDragUsesElasticResistanceBeforeRelease() {
        val screen = Rect(0, 0, 1156, 2510)
        val requested = Rect(-300, 2400, 856, 4249)

        val hard = BoundsPolicy.keepInnerEdgesVisible(requested, screen, 196, 0.25f)
        val resisted = BoundsPolicy.resistBeyondInnerEdges(requested, screen, 196, 0.25f)

        assertTrue(resisted.left < hard.left)
        assertTrue(resisted.left > requested.left)
        assertTrue(resisted.top > hard.top)
        assertTrue(resisted.top < requested.top)
    }

    @Test
    fun innerEdgesKeepRequestedDistanceOnEverySide() {
        val screen = Rect(0, 0, 1080, 2400)
        val window = Rect(-2000, -1800, -1460, -600)

        val topLeft = BoundsPolicy.keepInnerEdgesVisible(window, screen, 48)
        val bottomRight = BoundsPolicy.keepInnerEdgesVisible(
            Rect(2000, 3000, 2540, 4200),
            screen,
            48,
        )

        assertEquals(48, topLeft.right - screen.left)
        assertEquals(48, topLeft.bottom - screen.top)
        assertEquals(48, screen.right - bottomRight.right)
        assertEquals(48, screen.bottom - bottomRight.bottom)
    }

    @Test
    fun oversizedWindowKeepsItsSizeOnSmallDisplay() {
        val screen = Rect(0, 0, 720, 1280)
        val window = Rect(-5000, 3000, 1500, 6200)

        val safe = BoundsPolicy.keepInnerEdgesVisible(window, screen, 32)

        assertEquals(window.width(), safe.width())
        assertEquals(window.height(), safe.height())
        assertEquals(32, screen.bottom - safe.top)
    }

    @Test
    fun validWindowPositionDoesNotMove() {
        val screen = Rect(0, 0, 2200, 1800)
        val window = Rect(300, 200, 1500, 1200)

        assertEquals(window, BoundsPolicy.keepInnerEdgesVisible(window, screen, 80))
    }

    @Test
    fun fittingWindowKeepsBothLeftAndRightInnerEdgesAwayFromScreen() {
        val screen = Rect(0, 0, 1080, 2400)
        val left = BoundsPolicy.keepInnerEdgesVisible(Rect(0, 200, 500, 1000), screen, 196)
        val right = BoundsPolicy.keepInnerEdgesVisible(Rect(800, 200, 1300, 1000), screen, 196)

        assertEquals(0, left.left)
        assertEquals(196, screen.right - right.right)
    }

    @Test
    fun scaledWindowUsesVisiblePixelsForEachEdge() {
        val screen = Rect(0, 0, 1156, 2510)
        val window = Rect(-200, 2200, 956, 4710)

        val safe = BoundsPolicy.keepInnerEdgesVisible(window, screen, 196, 0.25f)

        assertEquals(196, safe.left + (safe.width() * 0.25f).toInt())
        assertEquals(196, screen.bottom - safe.top)
    }
}
