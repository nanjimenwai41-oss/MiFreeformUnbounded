package com.freeform.unbounded

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthFrameAccessPolicyTest {
    @Test
    fun wallpaperCanWriteAndSystemUiCanOnlyRead() {
        val wallpaper = setOf("com.miui.miwallpaper.snowmountain")
        val systemUi = setOf("com.android.systemui")
        assertTrue(DepthFrameAccessPolicy.canAccess(wallpaper, writing = true))
        assertTrue(DepthFrameAccessPolicy.canAccess(wallpaper, writing = false))
        assertTrue(DepthFrameAccessPolicy.canAccess(systemUi, writing = false))
        assertFalse(DepthFrameAccessPolicy.canAccess(systemUi, writing = true))
    }

    @Test
    fun unknownUidAndSimilarPackageCannotAccessFrames() {
        for (packages in listOf(emptySet(), setOf("other.app"), setOf("com.miui.miwallpaper.fake"))) {
            assertFalse(DepthFrameAccessPolicy.canAccess(packages, writing = true))
            assertFalse(DepthFrameAccessPolicy.canAccess(packages, writing = false))
        }
    }
}
