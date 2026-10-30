package com.freeform.unbounded

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockMaskTransportPolicyTest {
    @Test
    fun onlyExactAlphaPayloadWithinNativeBoundsIsAccepted() {
        assertTrue(ClockMaskTransportPolicy.isValidFrame(236, 512, 120_832))
        assertFalse(ClockMaskTransportPolicy.isValidFrame(236, 512, 483_328))
        assertFalse(ClockMaskTransportPolicy.isValidFrame(236, 512, 120_831))
        assertTrue(ClockMaskTransportPolicy.isValidFrame(472, 1024, 483_328))
        assertTrue(ClockMaskTransportPolicy.isValidFrame(1156, 2510, 2_901_560))
        assertFalse(ClockMaskTransportPolicy.isValidFrame(4097, 1, 4097))
        assertFalse(ClockMaskTransportPolicy.isValidFrame(4096, 4096, 16_777_216))
        assertFalse(ClockMaskTransportPolicy.isValidFrame(0, 512, 0))
        assertFalse(ClockMaskTransportPolicy.isValidFrame(Int.MAX_VALUE, Int.MAX_VALUE, 1))
    }

    @Test
    fun onlySystemUiCanRegisterReceiverAndOnlyExactWallpaperPackagesCanSend() {
        assertTrue(DepthFrameAccessPolicy.canRegisterClockMaskReceiver(setOf("com.android.systemui")))
        assertFalse(DepthFrameAccessPolicy.canPublishClockMask(setOf("com.android.systemui")))
        assertTrue(DepthFrameAccessPolicy.canPublishClockMask(setOf("com.miui.miwallpaper.snowmountain")))
        for (packages in listOf(emptySet(), setOf("com.miui.miwallpaper.fake"), setOf("com.android.systemui.fake"))) {
            assertFalse(DepthFrameAccessPolicy.canPublishClockMask(packages))
            assertFalse(DepthFrameAccessPolicy.canRegisterClockMaskReceiver(packages))
        }
    }
}
