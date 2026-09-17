package com.freeform.unbounded

import android.content.Context
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Captures the target-process evidence required to diagnose the video-depth pipeline. */
object PhoneDiagnosticsExporter {
    suspend fun export(context: Context): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, "ci17-diagnostics").apply { mkdirs() }
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val output = File(directory, "ci17-depth-$stamp.txt")
            output.bufferedWriter().use { writer ->
                writer.appendLine("CI17 Super Wallpaper Depth Diagnostic")
                writer.appendLine("CapturedAt=$stamp")
                writer.appendLine()
                appendSection(writer, "Device", "getprop ro.build.version.incremental; getprop ro.miui.ui.version.name; getprop ro.product.device")
                appendSection(writer, "DepthSettings", "settings get secure aod_using_super_wallpaper; settings get secure wallpaper_matting_support_2")
                appendSection(writer, "Wallpaper", "dumpsys wallpaper")
                appendSection(writer, "SystemUI", "dumpsys package com.android.systemui")
                appendSection(writer, "AOD", "dumpsys package com.miui.aod")
                appendSection(writer, "MiWallpaper", "dumpsys package com.miui.miwallpaper")
                appendSection(
                    writer,
                    "DepthLog",
                    "logcat -d -v threadtime -b all | grep -E 'LSPosedFramework|FreeformUnbounded|CI17|KeyguardDepthInteractor|VideoDepth|FastPlayer|IMiuiVideoDepthLastFrameCallback|MiWallpaper' | tail -n 12000",
                )
            }
            output
        }
    }

    fun shareUri(context: Context, file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.diagnostics",
        file,
    )

    private fun appendSection(writer: java.io.BufferedWriter, name: String, command: String) {
        writer.appendLine("===== $name =====")
        writer.appendLine(runAsRoot(command))
        writer.appendLine()
    }

    private fun runAsRoot(command: String): String = runCatching {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching "ERROR: command timed out after $COMMAND_TIMEOUT_SECONDS seconds"
        }
        if (process.exitValue() == 0) output.ifBlank { "(no output)" }
        else "ERROR: root command exited ${process.exitValue()}\n$output"
    }.getOrElse { error ->
        "ERROR: unable to run root command: ${error.javaClass.simpleName}: ${error.message}"
    }

    private const val COMMAND_TIMEOUT_SECONDS = 20L
}
