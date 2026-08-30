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
        val result = withContext(Dispatchers.IO) {
            val runningTargets = targets.filter(::isProcessRunning)
            when {
                runningTargets.isEmpty() -> RestartResult.NOT_RUNNING
                !hasRootAccess() -> RestartResult.NO_ROOT
                else -> {
                    // AOD can expose both its main and editor processes; attempt each one before
                    // reporting success so no stale editor process keeps the old configuration.
                    val restarted = runningTargets.map(::killProcess).any { it }
                    if (restarted) RestartResult.RESTARTED else RestartResult.FAILED
                }
            }
        }
        Toast.makeText(
            appContext,
            when (result) {
                RestartResult.RESTARTED -> "正在重启 $displayName"
                RestartResult.NOT_RUNNING -> "${displayName}未启动，无需重启"
                RestartResult.NO_ROOT -> "未获取 ROOT 权限，无法重启 $displayName"
                RestartResult.FAILED -> "重启 $displayName 失败，请稍后重试"
            },
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun isProcessRunning(target: String): Boolean = runCatching {
        val process = ProcessBuilder("pidof", target)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(3, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@runCatching false
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.exitValue() == 0 && output.isNotEmpty()
    }.getOrDefault(false)

    private fun hasRootAccess(): Boolean = runCatching {
        val process = ProcessBuilder("su", "-c", "id -u")
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(6, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@runCatching false
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.exitValue() == 0 && output.lineSequence().any { it.trim() == "0" }
    }.getOrDefault(false)

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

    private enum class RestartResult {
        RESTARTED,
        NOT_RUNNING,
        NO_ROOT,
        FAILED,
    }

    /** Kept for callers compiled against the pre-UI-split API. */
    @Deprecated("Use restartSystemUi or restartAod")
    suspend fun restart(context: Context) {
        restartSystemUi(context)
    }
}
