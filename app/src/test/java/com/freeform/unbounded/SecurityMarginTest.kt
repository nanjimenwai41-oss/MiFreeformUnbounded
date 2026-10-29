package com.freeform.unbounded

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurityMarginTest {
    @Test fun acceptsDistancesAboveOldSliderLimit() {
        listOf(321, 640, 1000, 2000).forEach { assertEquals(it, normalizeSecurityMargin(it)) }
    }

    @Test fun clampsOnlyAtExpandedBounds() {
        assertEquals(ModuleConfigKeys.MIN_MARGIN, normalizeSecurityMargin(-1))
        assertEquals(2000, normalizeSecurityMargin(Int.MAX_VALUE))
    }

    @Test fun preservesDefaultAndLegacyMigration() {
        assertEquals(196, normalizeSecurityMargin(196))
        assertEquals(196, normalizeSecurityMargin(48))
    }
}
