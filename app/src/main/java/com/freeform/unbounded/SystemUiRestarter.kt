package com.freeform.unbounded

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object SystemUiRestarter {
    suspend fun restartSystemUi(context: Context) {
        restartTargets(
            context = context,
            targets = listOf("com.android.systemui"),
            displayName = "系统界面",
        )
    }

    suspend fun restartAod(context: Context) {
        restartTargets(
            context = context,
            targets = listOf("com.miui.aod", "com.miui.aod:keyguardeditor"),
            displayName = "息屏与锁屏编辑",
        )
    }

    private suspend fun restartTargets(
        context: Context,
        targets: List<String>,
        displayName: String,
    ) {
        val appContext = context.applicationContext
        val success = withContext(Dispatchers.IO) {
            targets.any { target ->
                runCatching {
                    val process = ProcessBuilder("su", "-c", "killall $target")
                        .redirectErrorStream(true)
                        .start()
                    val finished = process.waitFor(6, TimeUnit.SECONDS)
                    if (!finished) {
                        process.destroy()
                        return@runCatching false
                    }
                    process.exitValue() == 0
                }.getOrDefault(false)
            }
        }
        Toast.makeText(
            appContext,
            if (success) {
                "正在重启 $displayName"
            } else {
                "未获取 ROOT 权限，无法重启 $displayName"
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    /** Kept for callers compiled against the pre-UI-split API. */
    @Deprecated("Use restartSystemUi or restartAod")
    suspend fun restart(context: Context) {
        restartSystemUi(context)
    }
}
