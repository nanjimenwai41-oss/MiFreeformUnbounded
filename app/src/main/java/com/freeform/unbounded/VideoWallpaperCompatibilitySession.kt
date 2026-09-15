package com.freeform.unbounded

import android.graphics.Rect

/** Local compatibility state used only while the depth algorithm is executing. */
internal enum class WallpaperKind { SUPER, VIDEO, IMAGE, UNKNOWN }

internal data class VideoDepthInput(
    val width: Int,
    val height: Int,
    val density: Float,
    val rotation: Int,
    val safeAreaReady: Boolean,
)

internal data class VideoDepthSessionSnapshot(
    val generation: Long,
    val originalKind: WallpaperKind,
    val compatibilityKind: WallpaperKind,
    val depthSupported: Boolean,
    val videoRenderingAllowed: Boolean,
    val initialized: Boolean,
    val input: VideoDepthInput?,
    val bounds: VideoDepthPolicy.SafeBounds?,
    val avoidRect: Rect?,
)

/**
 * A short-lived per-controller session. It deliberately does not change global
 * wallpaper settings or identity; callers must explicitly establish/invalidate it.
 */
internal class VideoWallpaperCompatibilitySession {
    private var generation = 0L
    private var originalKind = WallpaperKind.UNKNOWN
    private var compatibilityKind = WallpaperKind.UNKNOWN
    private var depthSupported = false
    private var videoRenderingAllowed = false
    private var initialized = false
    private var input: VideoDepthInput? = null
    private var bounds: VideoDepthPolicy.SafeBounds? = null
    private var avoidRect: Rect? = null

    @Synchronized
    fun establish(kind: WallpaperKind, depthEnabled: Boolean, input: VideoDepthInput? = null): Long {
        generation++
        originalKind = kind
        compatibilityKind = if (kind == WallpaperKind.SUPER && depthEnabled) WallpaperKind.VIDEO else kind
        depthSupported = compatibilityKind == WallpaperKind.VIDEO && depthEnabled
        videoRenderingAllowed = depthSupported
        initialized = false
        this.input = input
        bounds = null
        avoidRect = null
        return generation
    }

    @Synchronized
    fun invalidate() {
        generation++
        originalKind = WallpaperKind.UNKNOWN
        compatibilityKind = WallpaperKind.UNKNOWN
        depthSupported = false
        videoRenderingAllowed = false
        initialized = false
        input = null
        bounds = null
        avoidRect = null
    }

    @Synchronized
    fun updateInput(newInput: VideoDepthInput?) {
        input = newInput
        bounds = null
        avoidRect = null
    }

    @Synchronized
    fun markInitialized(newBounds: VideoDepthPolicy.SafeBounds?) {
        initialized = true
        bounds = newBounds
    }

    @Synchronized
    fun setAvoidRect(rect: Rect?) { avoidRect = rect?.let(::Rect) }

    @Synchronized
    fun snapshot(): VideoDepthSessionSnapshot = VideoDepthSessionSnapshot(
        generation, originalKind, compatibilityKind, depthSupported,
        videoRenderingAllowed, initialized, input, bounds, avoidRect?.let(::Rect),
    )
}
