package com.freeform.unbounded

import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal object BoundsPolicy {
    /**
     * Keeps the horizontal drag target while preserving the Y position produced by
     * the platform gesture code. Copying the old top coordinate here would make
     * vertical drags snap back on every frame.
     */
    fun keepHorizontalDragTarget(result: Rect, dragTarget: Rect): Rect = Rect(
        dragTarget.left,
        result.top,
        dragTarget.left + result.width(),
        result.bottom,
    )

    /** Restores the pointer-requested X/Y while retaining the platform's size. */
    fun keepRequestedDragTarget(result: Rect, dragTarget: Rect): Rect = Rect(
        dragTarget.left,
        dragTarget.top,
        dragTarget.left + result.width(),
        dragTarget.top + result.height(),
    )

    /**
     * Keeps at least [margin] screen pixels of the window visible on every side.
     *
     * This is deliberately not an inset rectangle.  A window may stay anywhere
     * inside the display; only the position at which its *inner* edge would leave
     * less than [margin] visible pixels is clamped.  [scaleX]/[scaleY] convert the
     * logical task bounds to the on-screen size used by MIUI freeform surfaces.
     */
    fun keepInnerEdgesVisible(
        bounds: Rect,
        screen: Rect,
        margin: Int,
        scaleX: Float = 1f,
        scaleY: Float = scaleX,
    ): Rect {
        if (bounds.isEmpty || screen.isEmpty) return Rect(bounds)

        val width = bounds.width()
        val height = bounds.height()
        val visibleWidth = max(1.0, width.toDouble() * scaleX.coerceAtLeast(0.01f))
        val visibleHeight = max(1.0, height.toDouble() * scaleY.coerceAtLeast(0.01f))
        // A margin larger than the visible window itself cannot be satisfied.  In
        // that case the whole window is the most useful lower bound.
        val horizontalMargin = min(
            margin.coerceAtLeast(0).toDouble(),
            min(visibleWidth, screen.width().toDouble()),
        )
        val verticalMargin = min(
            margin.coerceAtLeast(0).toDouble(),
            min(visibleHeight, screen.height().toDouble()),
        )

        val minLeft = ceil(screen.left - visibleWidth + horizontalMargin)
        val maxLeft = floor(screen.right - horizontalMargin)
        val minTop = ceil(screen.top - visibleHeight + verticalMargin)
        val maxTop = floor(screen.bottom - verticalMargin)
        val left = bounds.left.toDouble().coerceIn(minLeft, maxLeft)
        val top = bounds.top.toDouble().coerceIn(minTop, maxTop)

        return Rect(
            left.roundToIntSafe(),
            top.roundToIntSafe(),
            (left + width).roundToIntSafe(),
            (top + height).roundToIntSafe(),
        )
    }

    /**
     * Applies MIUI-like elastic resistance after crossing the hard visible-edge
     * limit. The requested position remains under the finger, but the extra travel
     * becomes progressively smaller and saturates at [maxOverscrollPx].
     */
    fun resistBeyondInnerEdges(
        bounds: Rect,
        screen: Rect,
        margin: Int,
        scaleX: Float = 1f,
        scaleY: Float = scaleX,
        maxOverscrollPx: Int = max(48, margin / 2),
    ): Rect {
        val hard = keepInnerEdgesVisible(bounds, screen, margin, scaleX, scaleY)
        if (hard == bounds) return Rect(bounds)

        val limit = max(1, maxOverscrollPx).toDouble()
        val left = resistAxis(bounds.left.toDouble(), hard.left.toDouble(), limit)
        val top = resistAxis(bounds.top.toDouble(), hard.top.toDouble(), limit)
        return Rect(
            left.roundToIntSafe(),
            top.roundToIntSafe(),
            (left + bounds.width()).roundToIntSafe(),
            (top + bounds.height()).roundToIntSafe(),
        )
    }

    private fun resistAxis(requested: Double, hardLimit: Double, maxOverscroll: Double): Double {
        val overshoot = requested - hardLimit
        if (overshoot == 0.0) return requested
        val resisted = maxOverscroll * (1.0 - exp(-kotlin.math.abs(overshoot) / maxOverscroll))
        return hardLimit + if (overshoot < 0.0) -resisted else resisted
    }

    private fun Double.roundToIntSafe(): Int =
        coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt()

}
