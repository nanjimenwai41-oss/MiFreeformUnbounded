package com.freeform.unbounded

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object SystemUiRestarter {
    private const val PACKAGE_AOD = "com.miui.aod"
    private const val PACKAGE_MI_WALLPAPER = "com.miui.miwallpaper"
    private const val BETWEEN_TARGETS_DELAY_MILLIS = 350L
    private val restartInFlight = AtomicBoolean(false)

    // Each Super wallpaper owns a separate package and can keep preview/render
    // child processes alive after the shared MiWallpaper service is stopped.
    private val superWallpaperTargets = listOf(
        Target.Package("com.miui.miwallpaper.moon"),
        Target.Package("com.miui.miwallpaper.snowmountain"),
        Target.Package("com.miui.miwallpaper.geometry"),
        Target.Package("com.miui.miwallpaper.saturn"),
        Target.Package("com.miui.miwallpaper.earth"),
        Target.Package("com.miui.miwallpaper.mars"),
    )

    suspend fun restartSystemUi(context: Context) {
        restartTargets(context, "系统界面", listOf(Target.Process("com.android.systemui")))
    }

    suspend fun restartAod(context: Context) {
        restartTargets(context, "Editor", listOf(Target.Package(PACKAGE_AOD)))
    }

    suspend fun restartMiWallpaper(context: Context) {
        restartTargets(context, "MiWallpaper", listOf(Target.Package(PACKAGE_MI_WALLPAPER)))
    }

    suspend fun restartMoon(context: Context) {
        restartTargets(context, "月球超级壁纸", listOf(superWallpaperTargets[0]))
    }

    suspend fun restartSnowMountain(context: Context) {
        restartTargets(context, "雪山超级壁纸", listOf(superWallpaperTargets[1]))
    }

    suspend fun restartGeometry(context: Context) {
        restartTargets(context, "几何超级壁纸", listOf(superWallpaperTargets[2]))
    }

    suspend fun restartSaturn(context: Context) {
        restartTargets(context, "土星超级壁纸", listOf(superWallpaperTargets[3]))
    }

    suspend fun restartEarth(context: Context) {
        restartTargets(context, "地球超级壁纸", listOf(superWallpaperTargets[4]))
    }

    suspend fun restartMars(context: Context) {
        restartTargets(context, "火星超级壁纸", listOf(superWallpaperTargets[5]))
    }

    suspend fun restartAll(context: Context) {
        // Keep this order aligned with the Home actions: SystemUI, Editor,
        // MiWallpaper, then each concrete Super wallpaper package.
        restartTargets(
            context,
            "全部静态作用域",
            listOf(
                Target.Process("com.android.systemui"),
                Target.Package(PACKAGE_AOD),
                Target.Package(PACKAGE_MI_WALLPAPER),
                *superWallpaperTargets.toTypedArray(),
            ),
        )
    }

    private suspend fun restartTargets(
        context: Context,
        displayName: String,
        targets: List<Target>,
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
                // Stop one scope at a time and leave a small recovery window so
                // a batch action cannot turn into a synchronized crash burst.
                buildList {
                    targets.forEachIndexed { index, target ->
                        add(stopTarget(target))
                        if (index < targets.lastIndex) delay(BETWEEN_TARGETS_DELAY_MILLIS)
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

    private fun stopTarget(target: Target): Boolean = runCatching {
        val command = when (target) {
            is Target.Process -> "killall ${target.processName}"
            is Target.Package -> "am force-stop ${target.packageName}"
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

    private sealed interface Target {
        data class Process(val processName: String) : Target
        data class Package(val packageName: String) : Target
    }

    /** Kept for callers compiled against the pre-UI-split API. */
    @Deprecated("Use one of the scoped restart methods")
    suspend fun restart(context: Context) {
        restartSystemUi(context)
    }
}
