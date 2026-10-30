package com.freeform.unbounded

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointRegistrationPolicyTest {
    @org.junit.Test fun startupDiagnosticIsOffByDefaultAndAlwaysBounded() {
        org.junit.Assert.assertEquals(0L, EndpointRegistrationPolicy.bootstrapDelayMillis(null))
        org.junit.Assert.assertEquals(0L, EndpointRegistrationPolicy.bootstrapDelayMillis("invalid"))
        org.junit.Assert.assertEquals(0L, EndpointRegistrationPolicy.bootstrapDelayMillis("-10"))
        org.junit.Assert.assertEquals(45_000L, EndpointRegistrationPolicy.bootstrapDelayMillis("45000"))
        org.junit.Assert.assertEquals(60_000L, EndpointRegistrationPolicy.bootstrapDelayMillis("1000000"))
    }
    @Test fun successfulRegistrationRenewsAfterProviderCouldHaveRestarted() {
        assertTrue(EndpointRegistrationPolicy.shouldRegister(true, 10_000_000_000L, 15_000_000_000L))
    }

    @Test fun registrationFailureRetriesWithoutFloodingProvider() {
        assertFalse(EndpointRegistrationPolicy.shouldRegister(false, 10_000_000_000L, 10_500_000_000L))
        assertTrue(EndpointRegistrationPolicy.shouldRegister(false, 10_000_000_000L, 11_000_000_000L))
    }

    @Test fun successfulRegistrationDoesNotCallProviderEveryFrame() {
        assertFalse(EndpointRegistrationPolicy.shouldRegister(true, 10_000_000_000L, 10_010_000_000L))
    }
}
