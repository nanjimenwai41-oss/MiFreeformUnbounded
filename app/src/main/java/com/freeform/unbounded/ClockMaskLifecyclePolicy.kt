package com.freeform.unbounded

/** 非交互不代表锁屏已消失；producer 停止也不代表壁纸会话结束。 */
internal object ClockMaskLifecyclePolicy {
    fun retainMask(keyguardShowing: Boolean, aodTransition: Boolean, superWallpaper: Boolean,
        sessionEnded: Boolean): Boolean = (keyguardShowing || aodTransition) && superWallpaper && !sessionEnded

    /** The verified OS4 linkage method receives show=false while beginning lockscreen-to-AOD. */
    fun beginAodTransition(show: Boolean, superWallpaper: Boolean, sessionEnded: Boolean): Boolean =
        !show && superWallpaper && !sessionEnded

    /**
     * A clear runnable is valid only while no newer frame or clear has advanced
     * the lifecycle generation. Session-ending clears are terminal when they
     * execute, but an old posted clear must never erase a newer session's frame.
     */
    fun acceptClear(requestGeneration: Long, currentGeneration: Long): Boolean =
        requestGeneration == currentGeneration
}
