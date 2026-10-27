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
        assertFalse(ClockMaskTransportPolicy.isValidFrame(513, 1, 513))
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
