package com.freeform.unbounded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoWallpaperCompatibilitySessionTest {
    @Test
    fun superWallpaperStaysNativeUntilFramePathIsReady() {
        val session = VideoWallpaperCompatibilitySession()

        session.establish(WallpaperKind.SUPER, depthEnabled = true, framePathReady = false)
        val native = session.snapshot()
        assertEquals(WallpaperKind.SUPER, native.compatibilityKind)
        assertFalse(native.depthSupported)

        session.establish(WallpaperKind.SUPER, depthEnabled = true, framePathReady = true)
        val compatible = session.snapshot()
        assertEquals(WallpaperKind.VIDEO, compatible.compatibilityKind)
        assertTrue(compatible.depthSupported)
        assertTrue(compatible.videoRenderingAllowed)
        assertTrue(compatible.generation > native.generation)
    }

    @Test
    fun disabledDepthNeverCreatesVideoCompatibility() {
        val session = VideoWallpaperCompatibilitySession()
        session.establish(WallpaperKind.SUPER, depthEnabled = false, framePathReady = true)

        val snapshot = session.snapshot()
        assertEquals(WallpaperKind.SUPER, snapshot.compatibilityKind)
        assertFalse(snapshot.depthSupported)
    }
}
