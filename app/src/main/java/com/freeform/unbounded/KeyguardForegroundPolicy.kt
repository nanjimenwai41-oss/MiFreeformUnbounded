package com.freeform.unbounded

/** OS4 DEX 已确认的锁屏状态门；不修改原厂主题和 AOD 条件。 */
internal object KeyguardForegroundPolicy {
    private const val KEYGUARD_STATUS_BAR_STATE = 1

    internal fun shouldShowVerifiedForeground(
        foregroundFrameAvailable: Boolean,
        superWallpaper: Boolean,
        keyguardShowing: Boolean,
        deviceInteractive: Boolean,
        wallpaperIsShow: Boolean,
    ): Boolean = foregroundFrameAvailable && superWallpaper && keyguardShowing &&
        deviceInteractive && wallpaperIsShow

    internal fun shouldReapplyKeyguardForegroundVisibility(
        statusBarState: Int?,
        defaultTheme: Boolean?,
        keyguardShowing: Boolean,
        deviceInteractive: Boolean,
        wallpaperIsShow: Boolean,
        foregroundFrameAvailable: Boolean,
        superWallpaper: Boolean,
    ): Boolean = statusBarState == KEYGUARD_STATUS_BAR_STATE && defaultTheme != false &&
        shouldShowVerifiedForeground(
            foregroundFrameAvailable = foregroundFrameAvailable,
            superWallpaper = superWallpaper,
            keyguardShowing = keyguardShowing,
            deviceInteractive = deviceInteractive,
            wallpaperIsShow = wallpaperIsShow,
        )

}
