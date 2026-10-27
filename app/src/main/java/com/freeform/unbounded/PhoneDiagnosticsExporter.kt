package com.freeform.unbounded

import android.content.Context
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.BufferedWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Captures a bugreport-style, root-backed device evidence bundle. */
object PhoneDiagnosticsExporter {
    suspend fun export(context: Context): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, "ci17-diagnostics").apply { mkdirs() }
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val output = File(directory, "ci17-depth-$stamp.txt")
            output.bufferedWriter().use { writer ->
                writer.appendLine("Freeform Unbounded Device Evidence Bundle")
                writer.appendLine("FormatVersion=2")
                writer.appendLine("CapturedAt=$stamp")
                writer.appendLine("CaptureMode=bugreport-style;full-logcat-buffers")
                writer.appendLine()
                appendCommandSection(writer, "CaptureMeta", "date; id; uptime; getprop ro.build.version.incremental; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.miui.ui.version.name; getprop ro.product.brand; getprop ro.product.model; getprop ro.product.device; getprop ro.build.fingerprint")
                appendCommandSection(writer, "AllProperties", "getprop")
                appendCommandSection(writer, "Kernel", "uname -a; cat /proc/version; cat /proc/cmdline")
                appendCommandSection(writer, "Memory", "cat /proc/meminfo; dumpsys meminfo")
                appendCommandSection(writer, "Cpu", "cat /proc/cpuinfo")
                appendCommandSection(writer, "Storage", "df -h; mount")
                appendCommandSection(writer, "BatteryAndThermal", "dumpsys battery; dumpsys thermalservice")
                appendCommandSection(writer, "Processes", "ps -A -o USER,PID,PPID,STAT,NAME,ARGS")
                appendCommandSection(writer, "DepthSettings", "settings list secure; settings list system | grep -i -E 'wallpaper|aod|depth|glass|clock'")
                appendCommandSection(writer, "Wallpaper", "dumpsys wallpaper")
                appendCommandSection(writer, "SurfaceFlinger", "dumpsys SurfaceFlinger")
                appendCommandSection(writer, "WindowManager", "dumpsys window")
                appendCommandSection(writer, "SystemUI", "dumpsys package com.android.systemui; dumpsys activity service com.android.systemui")
                appendCommandSection(writer, "AOD", "dumpsys package com.miui.aod; dumpsys activity service com.miui.aod")
                appendCommandSection(writer, "MiWallpaper", "dumpsys package com.miui.miwallpaper; dumpsys activity service com.miui.miwallpaper")
                appendCommandSection(writer, "XposedAndModules", "dumpsys package com.freeform.unbounded; dumpsys package org.lsposed.manager; logcat -b all -g")
                // Keep the complete contents of every available log buffer. Do
                // not filter or tail this section: filtering is performed after
                // export so no evidence is lost.
                appendCommandSection(writer, "LogcatAllBuffers", "logcat -d -v threadtime -b all")
                appendCommandSection(writer, "KernelLog", "dmesg -T")
            }
            output
        }
    }

    fun shareUri(context: Context, file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.diagnostics",
        file,
    )

    private fun appendCommandSection(writer: BufferedWriter, name: String, command: String) {
        writer.appendLine("===== $name =====")
        writer.appendLine("Command=$command")
        writer.appendLine("StartedAt=${System.currentTimeMillis()}")
        runAsRoot(command, writer)
        writer.appendLine()
    }

    private fun runAsRoot(command: String, writer: BufferedWriter) = runCatching {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        var lineCount = 0L
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                writer.appendLine(line)
                lineCount++
            }
        }
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            writer.appendLine("ERROR: command timed out after $COMMAND_TIMEOUT_SECONDS seconds")
            return@runCatching
        }
        writer.appendLine("ExitCode=${process.exitValue()}")
        writer.appendLine("Lines=$lineCount")
    }.onFailure { error ->
        writer.appendLine("ERROR: unable to run root command: ${error.javaClass.simpleName}: ${error.message}")
    }

    private const val COMMAND_TIMEOUT_SECONDS = 90L
}
