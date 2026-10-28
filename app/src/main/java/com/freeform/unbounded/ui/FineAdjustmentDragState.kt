package com.freeform.unbounded.ui

/** MIUIX 首次回调是触点的绝对位置，仅用作锚点，避免按下时数值跳变。 */
internal class FineAdjustmentDragState {
    private var previousRawValue: Float? = null

    fun adjust(currentValue: Float, rawValue: Float): Float {
        val previous = previousRawValue
        previousRawValue = rawValue
        return if (previous == null) currentValue else applyFineAdjustment(currentValue, rawValue, previous)
    }

    fun finish() {
        previousRawValue = null
    }
}
