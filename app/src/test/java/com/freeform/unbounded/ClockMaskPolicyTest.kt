package com.freeform.unbounded

import java.nio.ShortBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockMaskPolicyTest {
    @Test
    fun maskUsesSeparatedSubjectClusterAndFlipsGlBottomOrigin() {
        // The lower two GL rows contain the near mountain cluster. Display row 0 is top-origin.
        val depth = ShortBuffer.wrap(shortArrayOf(
            10, 10, 10, 10,
            10, 10, 10, 10,
            200, 200, 200, 200,
            200, 200, 200, 200,
        ))
        val alpha = ByteArray(16)
        val selected = ClockMaskPolicy.writeAlphaMask(depth, 4, 4, 100, 4, 4, alpha)

        assertEquals(8, selected)
        assertTrue(alpha.take(8).all { it == 0xff.toByte() })
        assertTrue(alpha.drop(8).all { it == 0.toByte() })
    }

    @Test
    fun outputIsBoundedAndInvalidDimensionsAreRejected() {
        assertEquals(ClockMaskPolicy.Size(236, 512), ClockMaskPolicy.outputSize(236, 512))
        assertEquals(ClockMaskPolicy.Size(1024, 512), ClockMaskPolicy.outputSize(1024, 512))
        assertEquals(ClockMaskPolicy.Size(472, 1024), ClockMaskPolicy.outputSize(1156, 2510))
        assertEquals(ClockMaskPolicy.Size(512, 256), ClockMaskPolicy.outputSize(1024, 512, maxDimension = 512))
        assertFalse(ClockMaskPolicy.outputSize(0, 10) != null)
    }
}
