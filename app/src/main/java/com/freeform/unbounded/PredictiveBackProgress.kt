package com.freeform.unbounded

/**
 * The pager can represent at most half a page as an offset fraction. Keep the configured
 * predictive preview below that physical limit. The system reports a normalized gesture progress;
 * scale it into the configured maximum so the preview follows the finger for the whole gesture
 * instead of reaching the maximum early and then sticking there.
 */
internal const val DEFAULT_MAX_PREDICTIVE_BACK_PROGRESS: Float = 0.35f
internal const val MIN_MAX_PREDICTIVE_BACK_PROGRESS: Float = 0.20f
internal const val MAX_MAX_PREDICTIVE_BACK_PROGRESS: Float = 0.50f

internal fun predictiveBackOffsetFraction(
    progress: Float,
    maxProgress: Float = DEFAULT_MAX_PREDICTIVE_BACK_PROGRESS,
): Float {
    val limit = maxProgress.coerceIn(
        MIN_MAX_PREDICTIVE_BACK_PROGRESS,
        MAX_MAX_PREDICTIVE_BACK_PROGRESS,
    )
    return progress.coerceIn(0f, 1f) * limit
}
