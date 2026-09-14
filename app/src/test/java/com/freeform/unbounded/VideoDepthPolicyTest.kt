package com.freeform.unbounded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDepthPolicyTest {
    @Test
    fun detectsSuperWallpaperTargetsOnlyWhenEnabled() {
        assertTrue(VideoDepthPolicy.shouldUseVideoDepth(SuperWallpaperTargetFixture(), true))
        assertFalse(VideoDepthPolicy.shouldUseVideoDepth(SuperWallpaperTargetFixture(), false))
        assertFalse(VideoDepthPolicy.shouldUseVideoDepth(PlainClockTarget(), true))
    }

    @Test
    fun detectsSuperWallpaperThroughWallpaperInfoAfterControllerRebind() {
        assertTrue(VideoDepthPolicy.shouldUseVideoDepth(RebindingController(), true))
    }

    @Test
    fun normalizesEvaluatorDpBoundsAndKeepsRatioCompatibility() {
        assertEquals(0.375f, VideoDepthPolicy.normalizeBound(300f, 2400, 3f)!!, 0f)
        assertEquals(0.5f, VideoDepthPolicy.normalizeBound(0.5f, 2400, 3f)!!, 0f)
        assertEquals(1f, VideoDepthPolicy.normalizeBound(2400f, 2400, 3f)!!, 0f)
        assertNull(VideoDepthPolicy.normalizeBound(Float.NaN, 2400, 3f))
        assertNull(VideoDepthPolicy.normalizeBound(300f, 0, 3f))
    }

    private class SuperWallpaperTargetFixture {
        val mKeyguardWallpaperType = "super_wallpaper"
    }

    private class PlainClockTarget

    private class RebindingController {
        val mWallpaperInfo = SuperWallpaperTargetFixture()
    }
}
