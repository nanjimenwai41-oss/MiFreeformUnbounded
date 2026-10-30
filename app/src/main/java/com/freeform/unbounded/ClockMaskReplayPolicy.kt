package com.freeform.unbounded

/** A stationary wallpaper still needs its last mask when SystemUI starts later. */
internal object ClockMaskReplayPolicy {
    fun shouldReplay(sequence: Long, deliveredSequence: Long, sameReceiver: Boolean): Boolean =
        sequence > 0 && (!sameReceiver || sequence > deliveredSequence)

    // A bootstrap snapshot is not a newly captured animation frame. Only the
    // receiver's first input may bypass the live frame age limit.
    fun acceptAge(ageNanos: Long, maxAgeNanos: Long, bootstrap: Boolean, firstInput: Boolean): Boolean =
        ageNanos >= 0 && (ageNanos <= maxAgeNanos || (bootstrap && firstInput))
}
