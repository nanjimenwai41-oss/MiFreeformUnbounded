package com.freeform.unbounded

import android.graphics.Rect
import kotlin.math.max

internal object BoundsPolicy {
    private const val MIN_HALF_SPAN = 100_000L
    private const val MAX_COORDINATE = 1_000_000L
    private const val SPAN_MULTIPLIER = 32L

    fun expand(source: Rect): Rect {
        val width = max(1L, source.width().toLong())
        val height = max(1L, source.height().toLong())
        val halfSpan = max(MIN_HALF_SPAN, max(width, height) * SPAN_MULTIPLIER)
            .coerceAtMost(MAX_COORDINATE)
        val centerX = (source.left.toLong() + source.right.toLong()) / 2L
        val centerY = (source.top.toLong() + source.bottom.toLong()) / 2L
        return Rect(
            (centerX - halfSpan).coerceIn(-MAX_COORDINATE, MAX_COORDINATE).toInt(),
            (centerY - halfSpan).coerceIn(-MAX_COORDINATE, MAX_COORDINATE).toInt(),
            (centerX + halfSpan).coerceIn(-MAX_COORDINATE, MAX_COORDINATE).toInt(),
            (centerY + halfSpan).coerceIn(-MAX_COORDINATE, MAX_COORDINATE).toInt(),
        )
    }

    fun expandMovable(source: Rect): Rect = Rect(
        source.left,
        source.top,
        source.right,
        MAX_MOVABLE_BOTTOM,
    )

    fun keepHorizontalDragTarget(result: Rect, dragTarget: Rect): Rect = Rect(
        dragTarget.left,
        result.top,
        dragTarget.right,
        result.bottom,
    )

    private const val MAX_MOVABLE_BOTTOM = 1_000_000
}
