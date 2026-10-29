package com.freeform.unbounded.ui

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.freeform.unbounded.RestartScope
import com.freeform.unbounded.RestartScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Icon

internal data class HomeDeviceInfo(
    val model: String,
    val androidVersion: String,
    val osVersion: String,
    val scopeVersions: Map<RestartScope, String>,
    val currentWallpaper: RestartScope?,
)

/** 回到应用、打开重启弹窗时更新，包管理器与属性读取均在后台执行。 */
@Composable
internal fun rememberDeviceInfo(dialogOpen: Boolean): State<HomeDeviceInfo?> {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val lifecycle = (context as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    return produceState<HomeDeviceInfo?>(null, context, refresh, dialogOpen) {
        value = withContext(Dispatchers.IO) { readDeviceInfo(context) }
    }
}

private fun readDeviceInfo(context: Context): HomeDeviceInfo {
    // getprop 是只读系统工具，避免隐藏 API 被系统拦截后误识别机型。
    val properties = runCatching {
        val process = ProcessBuilder("getprop").redirectErrorStream(true).start()
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val match = Regex("^\\[([^]]+)]: \\[([^]]*)]$").matchEntire(line)
                    match?.let { it.groupValues[1] to it.groupValues[2] }
                }.toMap()
            }
        } finally {
            process.destroy()
        }
    }.getOrDefault(emptyMap())
    val marketName = listOf("ro.product.marketname", "ro.product.odm.marketname", "ro.product.vendor.marketname")
        .firstNotNullOfOrNull { properties[it]?.takeIf(String::isNotBlank) }.orEmpty()
    val osVersion = formatOsVersion(
        properties["ro.mi.os.version.name"].orEmpty(),
        properties["ro.mi.os.version.incremental"].orEmpty().ifBlank { Build.VERSION.INCREMENTAL },
        properties["ro.miui.ui.version.name"].orEmpty(),
    )
    val currentPackage = runCatching {
        val manager = WallpaperManager.getInstance(context)
        val lockInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)
        } else null
        (lockInfo ?: manager.wallpaperInfo)?.packageName
    }.getOrNull()
    return HomeDeviceInfo(
        model = formatDeviceModel(marketName, Build.MANUFACTURER, Build.MODEL),
        androidVersion = "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
        osVersion = osVersion,
        scopeVersions = RestartScopes.allStatic.associateWith { installedVersion(context, it.packageName) },
        currentWallpaper = RestartScopes.wallpapers.firstOrNull { it.packageName == currentPackage },
    )
}

internal fun formatDeviceModel(marketName: String, manufacturer: String, model: String): String = when {
    marketName.isNotBlank() && !marketName.equals(model, ignoreCase = true) -> "$marketName · $model"
    marketName.isNotBlank() -> marketName
    model.startsWith(manufacturer, ignoreCase = true) -> model
    else -> listOf(manufacturer, model).filter(String::isNotBlank).joinToString(" ")
}.ifBlank { "未知设备" }

internal fun formatOsVersion(hyperName: String, incremental: String, miuiName: String): String = when {
    hyperName.isNotBlank() -> "HyperOS ${hyperName.removePrefix("OS")} · $incremental"
    incremental.startsWith("OS") -> "HyperOS ${incremental.removePrefix("OS")}"
    else -> listOf(miuiName, incremental).filter(String::isNotBlank).joinToString(" · ")
}.ifBlank { "未知版本" }

@Composable
internal fun DeviceInfoCard(info: HomeDeviceInfo?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        DeviceInfoRow(Icons.Rounded.Smartphone, "设备", info?.model ?: "正在读取…")
        DeviceInfoRow(Icons.Rounded.Android, "Android", info?.androidVersion ?: "正在读取…")
        DeviceInfoRow(Icons.Rounded.Info, "澎湃 OS", info?.osVersion ?: "正在读取…")
        val targets = listOfNotNull(info?.currentWallpaper) + RestartScopes.allStatic.filterNot { it == info?.currentWallpaper }
        targets.forEach { scope ->
            val current = scope == info?.currentWallpaper
            DeviceInfoRow(
                if (scope in RestartScopes.wallpapers || scope == RestartScopes.MI_WALLPAPER) Icons.Rounded.Wallpaper else Icons.Rounded.Layers,
                scope.label + if (current) " · 当前使用" else "",
                info?.scopeVersions?.get(scope) ?: "正在读取…",
            )
        }
    }
}

@Composable
private fun DeviceInfoRow(icon: ImageVector, title: String, summary: String) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = { Icon(icon, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
    )
}

@Suppress("DEPRECATION")
private fun installedVersion(context: Context, packageName: String): String = runCatching {
    val info = context.packageManager.getPackageInfo(packageName, 0)
    info.versionName?.takeIf(String::isNotBlank) ?: "版本 ${info.longVersionCode}"
}.getOrElse { "未安装" }
