package com.freeform.unbounded

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal object SystemUiRestarter {
    private val restartInFlight = AtomicBoolean(false)
    // 页面退出或重组不能取消已经确认的九作用域操作。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun restartSelected(context: Context, scopes: List<RestartScope>) {
        val targets = RestartPlan.ordered(scopes)
        if (targets.isEmpty()) return
        val appContext = context.applicationContext
        if (!restartInFlight.compareAndSet(false, true)) {
            Toast.makeText(appContext, "已有作用域正在重启", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val output = runCatching {
                        val process = ProcessBuilder("su", "-c", RestartPlan.script(targets))
                            .redirectErrorStream(true).start()
                        try {
                            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                                process.destroyForcibly()
                                ""
                            } else process.inputStream.bufferedReader().use { it.readText() }
                        } finally {
                            process.inputStream.close()
                            process.destroy()
                        }
                    }.getOrDefault("")
                    RestartPlan.results(output, targets)
                }
                val restarted = results.count { it.value == "restarted" }
                val inactive = results.count { it.value == "inactive" }
                val failed = results.filterValues { it == "failed" }.keys
                val message = buildString {
                    append("已检查 ${results.size} 个作用域：重启 $restarted 项")
                    if (inactive > 0) append("，未运行 $inactive 项")
                    if (failed.isNotEmpty()) append("；失败：${failed.joinToString("、") { it.label }}，请检查 ROOT 权限")
                }
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            } finally {
                restartInFlight.set(false)
            }
        }
    }
}
