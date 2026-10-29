package com.freeform.unbounded

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

internal object SystemUiRestarter {
    private const val BETWEEN_TARGETS_DELAY_MILLIS = 350L
    private val restartInFlight = AtomicBoolean(false)

    suspend fun restartSelected(
        context: Context,
        scopes: List<RestartScope>,
        displayName: String = scopes.joinToString("、") { it.label },
    ) {
        val uniqueScopes = scopes.distinctBy(RestartScope::id)
        if (uniqueScopes.isEmpty()) return
        restartTargets(context, displayName, uniqueScopes)
    }

    private suspend fun restartTargets(
        context: Context,
        displayName: String,
        scopes: List<RestartScope>,
    ) {
        if (!restartInFlight.compareAndSet(false, true)) {
            Toast.makeText(
                context.applicationContext,
                "已有作用域正在重启",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val appContext = context.applicationContext
        try {
            val results = withContext(Dispatchers.IO) {
                buildList {
                    scopes.forEachIndexed { index, scope ->
                        add(stopTarget(scope))
                        if (index < scopes.lastIndex) delay(BETWEEN_TARGETS_DELAY_MILLIS)
                    }
                }
            }
            val succeeded = results.count { it }
            val failed = results.size - succeeded
            val message = when {
                succeeded == results.size -> "已请求重启 $displayName（$succeeded 项）"
                succeeded == 0 -> "重启 $displayName 失败，可能为未获取 ROOT 权限或目标未启动"
                else -> "已请求重启 $displayName：成功 $succeeded 项，失败 $failed 项"
            }
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        } finally {
            restartInFlight.set(false)
        }
    }

    private fun stopTarget(scope: RestartScope): Boolean = runCatching {
        val command = if (scope.isProcess) {
            "killall ${scope.packageName}"
        } else {
            "am force-stop ${scope.packageName}"
        }
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(6, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@runCatching false
        }
        process.exitValue() == 0
    }.getOrDefault(false)

}
