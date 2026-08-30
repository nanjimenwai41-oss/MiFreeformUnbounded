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
        val restarted = withContext(Dispatchers.IO) {
            // Let the privileged kill command be the single source of truth. A non-zero exit
            // covers both missing ROOT access and a target process that is not running.
            targets.map(::killProcess).any { it }
        }
        Toast.makeText(
            appContext,
            if (restarted) {
                "正在重启 $displayName"
            } else {
                "重启 $displayName 失败，可能为未获取 ROOT 权限或${displayName}未启动"
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun killProcess(target: String): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", "killall $target")
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(6, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@runCatching false
        }
        process.exitValue() == 0
    }.getOrDefault(false)

    /** Kept for callers compiled against the pre-UI-split API. */
    @Deprecated("Use restartSystemUi or restartAod")
    suspend fun restart(context: Context) {
        restartSystemUi(context)
    }
}
