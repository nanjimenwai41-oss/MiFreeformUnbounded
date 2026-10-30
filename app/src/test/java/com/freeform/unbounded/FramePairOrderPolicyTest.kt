package com.freeform.unbounded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FramePairOrderPolicyTest {
    private fun frame(stamp: Long, buffer: Boolean = true, mask: Boolean = true, registered: Boolean = true) =
        FramePairOrderPolicy.Readiness(stamp, registered, buffer, mask)

    @Test fun lateOlderCallbackBlocksNewerCompletePair() {
        assertNull(FramePairOrderPolicy.newestCompletePrefix(listOf(frame(1, buffer = false), frame(2)), 0))
    }

    @Test fun maskArrivingBeforeSwapRegistrationCannotBeSent() {
        assertNull(FramePairOrderPolicy.newestCompletePrefix(listOf(frame(1, registered = false), frame(2)), 0))
    }

    @Test fun unpairedIntermediateBuffersMergeIntoNextCompletePair() {
        assertEquals(3L, FramePairOrderPolicy.newestCompletePrefix(listOf(frame(1), frame(2, mask = false), frame(3)), 0))
    }

    @Test fun watermarkPreventsCaptureTimestampRegression() {
        assertNull(FramePairOrderPolicy.newestCompletePrefix(listOf(frame(1), frame(2)), 2))
        assertEquals(3L, FramePairOrderPolicy.newestCompletePrefix(listOf(frame(1), frame(2), frame(3)), 2))
    }
}
