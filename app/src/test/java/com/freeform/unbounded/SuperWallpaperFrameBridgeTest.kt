package com.freeform.unbounded

import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class SuperWallpaperFrameBridgeTest {
    @After
    fun clearProcessor() {
        SuperWallpaperFrameBridge.installProcessor(null)
    }

    @Test
    fun returnsOriginalWhenNoProcessorIsInstalled() {
        val original = ByteBuffer.allocate(4)
        assertSame(original, SuperWallpaperFrameBridge.processOrOriginal(listOf(original), original))
    }

    @Test
    fun rejectsEmptyReplacementBuffer() {
        val frame = ByteBuffer.allocate(4)
        val empty = ByteBuffer.allocate(0)
        SuperWallpaperFrameBridge.installProcessor { empty }
        assertSame(frame, SuperWallpaperFrameBridge.processOrOriginal(listOf(frame), frame))
    }

    @Test
    fun acceptsCompatibleBufferOnlyThroughInstalledProcessor() {
        val frame = ByteBuffer.allocate(4)
        val replacement = ByteBuffer.allocate(4)
        SuperWallpaperFrameBridge.installProcessor { replacement }
        assertSame(replacement, SuperWallpaperFrameBridge.processOrOriginal(listOf(frame), frame))
    }

    @Test
    fun pairedDepthAndForegroundCanBeRecognizedAsOneSample() {
        assertTrue(SuperWallpaperFrameBridge.hasMatchingForeground(12, 12))
        assertFalse(SuperWallpaperFrameBridge.hasMatchingForeground(12, 11))
        assertFalse(SuperWallpaperFrameBridge.hasMatchingForeground(12, 0))
        assertFalse(SuperWallpaperFrameBridge.hasMatchingForeground(0, 0))
    }

    @Test
    fun newerForegroundFramesStayUsableWhileDepthRefreshesMoreSlowly() {
        assertTrue(SuperWallpaperFrameBridge.hasCurrentForeground(12, 12))
        assertTrue(SuperWallpaperFrameBridge.hasCurrentForeground(12, 17))
        assertFalse(SuperWallpaperFrameBridge.hasCurrentForeground(12, 11))
        assertFalse(SuperWallpaperFrameBridge.hasCurrentForeground(12, 0))
        assertFalse(SuperWallpaperFrameBridge.hasCurrentForeground(0, 17))
    }

    @Test
    fun newerForegroundRemainsRenderableWithoutPretendingItIsAnExactDepthPair() {
        assertTrue(SuperWallpaperFrameBridge.hasMatchingForeground(12, 12))
        assertFalse(SuperWallpaperFrameBridge.hasMatchingForeground(12, 17))
        assertTrue(SuperWallpaperFrameBridge.hasCurrentForeground(12, 17))
        assertFalse(SuperWallpaperFrameBridge.hasCurrentForeground(0, 17))
    }

}
