package com.freeform.unbounded

/**
 * The pager can represent at most half a page as an offset fraction. Keep the predictive preview
 * below that physical limit and leave the remainder of a completed back gesture to the pager's
 * spring animation.
 */
internal const val DEFAULT_MAX_PREDICTIVE_BACK_PROGRESS: Float = 0.35f
internal const val MIN_MAX_PREDICTIVE_BACK_PROGRESS: Float = 0.20f
internal const val MAX_MAX_PREDICTIVE_BACK_PROGRESS: Float = 0.50f

internal fun clampPredictiveBackProgress(
    progress: Float,
    maxProgress: Float = DEFAULT_MAX_PREDICTIVE_BACK_PROGRESS,
): Float {
    val limit = maxProgress.coerceIn(
        MIN_MAX_PREDICTIVE_BACK_PROGRESS,
        MAX_MAX_PREDICTIVE_BACK_PROGRESS,
    )
    return progress.coerceIn(0f, limit)
}
