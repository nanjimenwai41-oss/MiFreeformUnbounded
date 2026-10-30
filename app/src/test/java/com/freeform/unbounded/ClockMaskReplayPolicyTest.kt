package com.freeform.unbounded

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockMaskReplayPolicyTest {
    @Test fun lateSystemUiReceivesTheStationaryFinalMask() {
        assertTrue(ClockMaskReplayPolicy.shouldReplay(12, 0, false))
        assertTrue(ClockMaskReplayPolicy.acceptAge(30_000_000_000, 150_000_000, true, true))
    }
    @Test fun receiverRestartReplaysEvenAnAlreadyDeliveredMask() {
        assertTrue(ClockMaskReplayPolicy.shouldReplay(12, 12, false))
        assertFalse(ClockMaskReplayPolicy.shouldReplay(12, 12, true))
    }
    @Test fun failedDeliveryRetriesButClearedSessionDoesNot() {
        assertTrue(ClockMaskReplayPolicy.shouldReplay(13, 12, true))
        assertFalse(ClockMaskReplayPolicy.shouldReplay(0, 12, false))
    }
    @Test fun bootstrapCannotExcuseDelayedAnimationOrReplaceNewerInput() {
        assertFalse(ClockMaskReplayPolicy.acceptAge(200_000_000, 150_000_000, false, true))
        assertFalse(ClockMaskReplayPolicy.acceptAge(200_000_000, 150_000_000, true, false))
        assertFalse(ClockMaskReplayPolicy.acceptAge(-1, 150_000_000, true, true))
    }
}
