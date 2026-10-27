package com.freeform.unbounded

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockMaskLifecyclePolicyTest {
    @Test fun producerStopDuringLockscreenFadeKeepsTheLastMask() {
        // 进入 AOD 的非交互事件不能改变锁屏仍可见这一事实。
        assertTrue(ClockMaskLifecyclePolicy.retainMask(false, true, true, false))
    }

    @Test fun surfaceRecreationDoesNotUnmaskTheClock() {
        repeat(3) { assertTrue(ClockMaskLifecyclePolicy.retainMask(true, false, true, false)) }
    }

    @Test fun unlockRemovesTheMask() {
        assertFalse(ClockMaskLifecyclePolicy.retainMask(false, false, true, false))
    }

    @Test fun ordinaryWallpaperCannotInheritTheMask() {
        assertFalse(ClockMaskLifecyclePolicy.retainMask(true, false, false, false))
    }

    @Test fun explicitSessionEndOverridesAodTransition() {
        assertFalse(ClockMaskLifecyclePolicy.retainMask(false, true, true, true))
    }

    @Test fun verifiedHideLinkageArmsAodRetentionBeforeTheAnimationStarts() {
        assertTrue(ClockMaskLifecyclePolicy.beginAodTransition(show = false, superWallpaper = true, sessionEnded = false))
    }

    @Test fun showLinkageAndUnverifiedWallpaperDoNotArmAodRetention() {
        assertFalse(ClockMaskLifecyclePolicy.beginAodTransition(show = true, superWallpaper = true, sessionEnded = false))
        assertFalse(ClockMaskLifecyclePolicy.beginAodTransition(show = false, superWallpaper = false, sessionEnded = false))
        assertFalse(ClockMaskLifecyclePolicy.beginAodTransition(show = false, superWallpaper = true, sessionEnded = true))
    }

    @Test fun currentSessionEndIsAccepted() {
        assertTrue(ClockMaskLifecyclePolicy.acceptClear(5L, 5L))
    }

    @Test fun supersededSessionEndingClearCannotEraseANewerFrame() {
        // Receiving the newer frame advances the lifecycle generation before
        // the old clear runnable gets a chance to execute.
        assertFalse(ClockMaskLifecyclePolicy.acceptClear(4L, 5L))
    }

    @Test fun supersededTransientClearIsIgnored() {
        assertFalse(ClockMaskLifecyclePolicy.acceptClear(4L, 5L))
        assertTrue(ClockMaskLifecyclePolicy.acceptClear(5L, 5L))
    }
}
