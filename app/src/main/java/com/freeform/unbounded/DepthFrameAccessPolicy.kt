package com.freeform.unbounded

/** 限制跨进程帧写入来自已核实的壁纸作用域，SystemUI 只读取。 */
internal object DepthFrameAccessPolicy {
    private val writers = setOf(
        "com.miui.miwallpaper",
        "com.miui.miwallpaper.moon",
        "com.miui.miwallpaper.snowmountain",
        "com.miui.miwallpaper.geometry",
        "com.miui.miwallpaper.saturn",
        "com.miui.miwallpaper.earth",
        "com.miui.miwallpaper.mars",
    )

    fun canAccess(packages: Set<String>, writing: Boolean): Boolean = packages.any {
        it in writers || (!writing && it == "com.android.systemui")
    }

    fun canPublishClockMask(packages: Set<String>): Boolean = packages.any { it in writers }

    fun canRegisterClockMaskReceiver(packages: Set<String>): Boolean =
        "com.android.systemui" in packages
}
