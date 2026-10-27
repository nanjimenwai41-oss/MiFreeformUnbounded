package com.freeform.unbounded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDepthPolicyTest {
    @After
    fun clearSessionState() {
        VideoDepthPolicy.invalidateSession(null)
    }

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
    fun ordinaryWallpaperInvalidatesSuperSessionBeforeReturningToSuper() {
        val target = SwitchableWallpaperTarget()
        val first = VideoDepthPolicy.establishSession(target, true)!!

        target.mKeyguardWallpaperType = "image"
        assertFalse(VideoDepthPolicy.isSuperWallpaperTarget(target))
        assertNull(VideoDepthPolicy.sessionSnapshot(target))

        target.mKeyguardWallpaperType = "video"
        assertFalse(VideoDepthPolicy.isSuperWallpaperTarget(target))
        assertNull(VideoDepthPolicy.sessionSnapshot(target))

        target.mKeyguardWallpaperType = "super_wallpaper"
        val restored = VideoDepthPolicy.establishSession(target, true)!!
        assertTrue(restored.generation > first.generation)
        assertEquals(WallpaperKind.SUPER, restored.originalKind)
    }

    @Test
    fun recognizesStockSubjectMaskDepthReadiness() {
        assertTrue(VideoDepthPolicy.isDepthInputReady(ReadySuperWallpaperTarget()))
        assertFalse(VideoDepthPolicy.isDepthInputReady(PlainClockTarget()))
    }

    @Test
    fun nestedOrdinaryWallpaperClearsSuperIdentity() {
        val target = NestedSwitchableTarget()
        assertTrue(VideoDepthPolicy.isSuperWallpaperTarget(target))

        target.mWallpaperData = WallpaperInfoFixture("image", 20000)
        assertFalse(VideoDepthPolicy.isSuperWallpaperTarget(target))
        assertFalse(VideoDepthPolicy.shouldEnableDepthEffect(target, false))

        target.mWallpaperData = WallpaperInfoFixture("super_wallpaper", 0)
        assertTrue(VideoDepthPolicy.isSuperWallpaperTarget(target))
    }

    @Test
    fun subjectMaskDepthEffectTurnsOnOnlyForReadySuperWallpaper() {
        assertTrue(VideoDepthPolicy.shouldEnableDepthEffect(ReadySuperWallpaperTarget(), false))
        assertFalse(VideoDepthPolicy.shouldEnableDepthEffect(PlainClockTarget(), false))
        assertTrue(VideoDepthPolicy.shouldEnableDepthEffect(PlainClockTarget(), true))
    }

    @Test
    fun verifiedForegroundRequiresDepthBackedFrameOnVisibleKeyguard() {
        assertTrue(
            KeyguardForegroundPolicy.shouldShowVerifiedForeground(
                foregroundFrameAvailable = true,
                superWallpaper = true,
                keyguardShowing = true,
                deviceInteractive = true,
                wallpaperIsShow = true,
            ),
        )
        assertFalse(
            KeyguardForegroundPolicy.shouldShowVerifiedForeground(
                foregroundFrameAvailable = false,
                superWallpaper = true,
                keyguardShowing = true,
                deviceInteractive = true,
                wallpaperIsShow = true,
            ),
        )
        assertFalse(
            KeyguardForegroundPolicy.shouldShowVerifiedForeground(
                foregroundFrameAvailable = true,
                superWallpaper = false,
                keyguardShowing = true,
                deviceInteractive = true,
                wallpaperIsShow = true,
            ),
        )
        assertFalse(
            KeyguardForegroundPolicy.shouldShowVerifiedForeground(
                foregroundFrameAvailable = true,
                superWallpaper = true,
                keyguardShowing = false,
                deviceInteractive = true,
                wallpaperIsShow = true,
            ),
        )
        assertFalse(
            KeyguardForegroundPolicy.shouldShowVerifiedForeground(
                foregroundFrameAvailable = true,
                superWallpaper = true,
                keyguardShowing = true,
                deviceInteractive = true,
                wallpaperIsShow = false,
            ),
        )
        assertFalse(
            KeyguardForegroundPolicy.shouldShowVerifiedForeground(
                foregroundFrameAvailable = true,
                superWallpaper = true,
                keyguardShowing = true,
                deviceInteractive = false,
                wallpaperIsShow = true,
            ),
        )
    }

    @Test
    fun panelForegroundVisibilityIsReappliedOnlyForVisibleStockKeyguard() {
        assertTrue(
            KeyguardForegroundPolicy.shouldReapplyKeyguardForegroundVisibility(
                statusBarState = 1,
                defaultTheme = true,
                keyguardShowing = true,
                deviceInteractive = true,
                wallpaperIsShow = true,
                foregroundFrameAvailable = true,
                superWallpaper = true,
            ),
        )
        assertFalse(
            KeyguardForegroundPolicy.shouldReapplyKeyguardForegroundVisibility(
                statusBarState = 0,
                defaultTheme = true,
                keyguardShowing = true,
                deviceInteractive = true,
                wallpaperIsShow = true,
                foregroundFrameAvailable = true,
                superWallpaper = true,
            ),
        )
        assertFalse(
            KeyguardForegroundPolicy.shouldReapplyKeyguardForegroundVisibility(
                statusBarState = 1,
                defaultTheme = false,
                keyguardShowing = true,
                deviceInteractive = true,
                wallpaperIsShow = true,
                foregroundFrameAvailable = true,
                superWallpaper = true,
            ),
        )
        assertFalse(
            KeyguardForegroundPolicy.shouldReapplyKeyguardForegroundVisibility(
                statusBarState = 1,
                defaultTheme = true,
                keyguardShowing = true,
                deviceInteractive = true,
                wallpaperIsShow = true,
                foregroundFrameAvailable = false,
                superWallpaper = true,
            ),
        )
    }

    @Test
    fun forceStaticSubjectDepthEnablesPanelAndInteractorState() {
        val target = StaticDepthPanelFixture()

        assertTrue(VideoDepthPolicy.forceStaticSubjectDepth(target))
        assertTrue(target.depthEffectEnable)
        assertTrue(target.keyguardDepthInteractor.depthEffectEnableInner)
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

    private class SwitchableWallpaperTarget {
        var mKeyguardWallpaperType = "super_wallpaper"
    }

    private class ReadySuperWallpaperTarget {
        val mKeyguardWallpaperType = "super_wallpaper"
        val mDepthVideoReady = true
    }

    private class StaticDepthPanelFixture {
        var depthEffectEnable = false
        val keyguardDepthInteractor = StaticDepthInteractorFixture()
    }

    private class StaticDepthInteractorFixture {
        var depthEffectEnableInner = false
    }

    private class NestedSwitchableTarget {
        var mWallpaperData = WallpaperInfoFixture("super_wallpaper", 0)
    }

    private data class WallpaperInfoFixture(
        val resourceType: String,
        val magicType: Int,
    )
}
