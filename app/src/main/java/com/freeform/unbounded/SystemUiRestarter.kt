package com.freeform.unbounded

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object SystemUiRestarter {
    suspend fun restart(context: Context) {
        val appContext = context.applicationContext
        val success = withContext(Dispatchers.IO) {
            runCatching {
                val process = ProcessBuilder("su", "-c", "killall com.android.systemui")
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
        Toast.makeText(
            appContext,
            if (success) "正在重启系统界面" else "未获取 ROOT 权限，无法重启系统界面",
            Toast.LENGTH_SHORT,
        ).show()
    }
}
