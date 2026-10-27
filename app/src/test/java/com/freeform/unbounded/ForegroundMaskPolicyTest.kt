package com.freeform.unbounded

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundMaskPolicyTest {
    private val direction = ForegroundDepthDirection.LOW_VALUES_ARE_FOREGROUND

    @Test
    fun onlyBrightNearPixelsInsideSceneBecomeForeground() {
        // Depth readback is bottom-origin. Display y=26 maps to sourceY=73
        // and remains in the top sky; display y=27 maps to sourceY=72 and is
        // the first eligible scene row.
        assertFalse(ForegroundMaskPolicy.isForeground(2, 255, 255, 255, 73, 100, direction))
        assertTrue(ForegroundMaskPolicy.isForeground(2, 255, 255, 255, 72, 100, direction))
        // A cleared/black tile must stay transparent instead of covering the clock.
        assertFalse(ForegroundMaskPolicy.isForeground(2, 7, 7, 7, 60, 100, direction))
        // Far pixels are background, even when they are bright.
        assertFalse(ForegroundMaskPolicy.isForeground(12_001, 255, 255, 255, 60, 100, direction))
    }

    @Test
    fun rejectsInvalidSourceRows() {
        assertFalse(ForegroundMaskPolicy.isForeground(2, 255, 255, 255, -1, 100, direction))
        assertFalse(ForegroundMaskPolicy.isForeground(2, 255, 255, 255, 100, 100, direction))
        assertFalse(ForegroundMaskPolicy.isForeground(2, 255, 255, 255, 0, 0, direction))
    }

    @Test
    fun coverageGateRejectsAFullScreenMaskAndAllowsALocalSnowPatch() {
        assertFalse(ForegroundMaskPolicy.isCoverageSafe(40_001, 100_000))
        assertTrue(ForegroundMaskPolicy.isCoverageSafe(10_000, 100_000))
        assertFalse(ForegroundMaskPolicy.isCoverageSafe(0, 100_000))
    }

    @Test
    fun reversedDepthKeepsDarkAndDistantGeometryAndRejectsClearedSky() {
        val reversed = ForegroundDepthDirection.NONZERO_IS_GEOMETRY
        assertFalse(ForegroundMaskPolicy.isForeground(0, 255, 255, 255, 60, 100, reversed))
        assertTrue(ForegroundMaskPolicy.isForeground(1, 0, 0, 0, 60, 100, reversed))
        assertTrue(ForegroundMaskPolicy.isForeground(65_535, 255, 255, 255, 80, 100, reversed))
        assertTrue(ForegroundMaskPolicy.isGeometryCoverageSafe(70, 100))
        assertFalse(ForegroundMaskPolicy.isGeometryCoverageSafe(100, 100))
    }

    @Test
    fun transparentOverlayRejectsNearBlackCaptureArtifactsOnly() {
        assertFalse(ForegroundMaskPolicy.isRenderableForeground(true, 7, 4, 6, 8))
        assertTrue(ForegroundMaskPolicy.isRenderableForeground(true, 8, 0, 0, 8))
        assertFalse(ForegroundMaskPolicy.isRenderableForeground(false, 255, 255, 255, 8))
    }
}
