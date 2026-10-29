package com.freeform.unbounded

/** 首页重启与设备版本信息共用的静态作用域。 */
internal data class RestartScope(
    val id: String,
    val label: String,
    val packageName: String,
    val isProcess: Boolean = false,
)

internal object RestartScopes {
    val SYSTEM_UI = RestartScope("system_ui", "系统界面", "com.android.systemui", isProcess = true)
    val AOD = RestartScope("aod", "息屏与锁屏编辑", "com.miui.aod")
    val MI_WALLPAPER = RestartScope("miwallpaper", "MiWallpaper", "com.miui.miwallpaper")
    val MOON = RestartScope("moon", "月球超级壁纸", "com.miui.miwallpaper.moon")
    val SNOW_MOUNTAIN = RestartScope("snowmountain", "雪山超级壁纸", "com.miui.miwallpaper.snowmountain")
    val GEOMETRY = RestartScope("geometry", "几何超级壁纸", "com.miui.miwallpaper.geometry")
    val SATURN = RestartScope("saturn", "土星超级壁纸", "com.miui.miwallpaper.saturn")
    val EARTH = RestartScope("earth", "地球超级壁纸", "com.miui.miwallpaper.earth")
    val MARS = RestartScope("mars", "火星超级壁纸", "com.miui.miwallpaper.mars")

    val wallpapers = listOf(MOON, SNOW_MOUNTAIN, GEOMETRY, SATURN, EARTH, MARS)
    val allStatic = listOf(SYSTEM_UI, AOD, MI_WALLPAPER) + wallpapers

    fun currentWallpapers(current: RestartScope?): Set<RestartScope> =
        setOf(MI_WALLPAPER) + listOfNotNull(current?.takeIf { it in wallpapers })

    fun currentScopes(current: RestartScope?): Set<RestartScope> =
        setOf(SYSTEM_UI, AOD) + currentWallpapers(current)
}
