package com.freeform.unbounded.ui

import android.content.Intent
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.freeform.unbounded.BuildConfig
import com.freeform.unbounded.ConfigRepository
import com.freeform.unbounded.ModuleStatus
import com.freeform.unbounded.ModuleStatusRepository
import com.freeform.unbounded.PhoneDiagnosticsExporter
import com.freeform.unbounded.SystemUiRestarter
import com.freeform.unbounded.ui.theme.HyperRed
import com.freeform.unbounded.ui.theme.HyperRedContainerDark
import com.freeform.unbounded.ui.theme.HyperRedContainerLight
import com.freeform.unbounded.ui.theme.isInDarkTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
internal fun HomeScreen(
    enableBlur: Boolean,
    contentBottomPadding: Dp,
    monetEnabled: Boolean,
) {
    val status by ModuleStatusRepository.status.collectAsState()
    val config by ConfigRepository.config.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var diagnosticsStatus by remember { mutableStateOf<String?>(null) }
    var exportingDiagnostics by remember { mutableStateOf(false) }
    var showRestartMenu by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(blurBackdrop) {
                TopAppBar(
                    color = barColor,
                    title = "首页",
                    largeTitle = "Freeform Unbounded",
                    scrollBehavior = scrollBehavior,
                    actions = {
                        RestartTargetsPopup(
                            show = showRestartMenu,
                            onShowChange = { showRestartMenu = it },
                            onRestartSystemUi = { scope.launch { SystemUiRestarter.restartSystemUi(context) } },
                            onRestartAod = { scope.launch { SystemUiRestarter.restartAod(context) } },
                            onRestartMiWallpaper = { scope.launch { SystemUiRestarter.restartMiWallpaper(context) } },
                            onRestartMoon = { scope.launch { SystemUiRestarter.restartMoon(context) } },
                            onRestartSnowMountain = { scope.launch { SystemUiRestarter.restartSnowMountain(context) } },
                            onRestartGeometry = { scope.launch { SystemUiRestarter.restartGeometry(context) } },
                            onRestartSaturn = { scope.launch { SystemUiRestarter.restartSaturn(context) } },
                            onRestartEarth = { scope.launch { SystemUiRestarter.restartEarth(context) } },
                            onRestartMars = { scope.launch { SystemUiRestarter.restartMars(context) } },
                            onRestartAll = { scope.launch { SystemUiRestarter.restartAll(context) } },
                        )
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                overscrollEffect = null,
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = 12.dp,
                    bottom = contentBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    KernelStyleStatusCard(
                        status = status,
                        monetEnabled = monetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        safetyMode = config.safetyModeEnabled,
                    )
                }
                item {
                    RestartPrimaryCard(
                        onRestartSystemUi = { scope.launch { SystemUiRestarter.restartSystemUi(context) } },
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeading(title = "功能状态", summary = "当前配置")
                        FeatureStatusCard(
                        freeformEnabled = config.freeformBoundaryEnabled,
                        glassClockEnabled = config.aodGlassEnabled,
                        superWallpaperDepthEnabled = config.superWallpaperDepthEnabled,
                        )
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeading(title = "设备信息", summary = "运行环境")
                        DeviceInfoCard()
                    }
                }
                item {
                    DiagnosticsCard(
                        status = diagnosticsStatus,
                        exporting = exportingDiagnostics,
                        onExport = {
                            exportingDiagnostics = true
                            diagnosticsStatus = "正在生成设备诊断包…"
                            scope.launch {
                                PhoneDiagnosticsExporter.export(context)
                                    .onSuccess { file ->
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_STREAM,
                                                PhoneDiagnosticsExporter.shareUri(context, file),
                                            )
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "导出景深诊断日志"))
                                        diagnosticsStatus = "已生成，可在分享面板保存或发送"
                                    }
                                    .onFailure { error ->
                                        diagnosticsStatus = "导出失败：${error.message ?: error.javaClass.simpleName}"
                                    }
                                exportingDiagnostics = false
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    summary: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
        Text(
            summary,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun KernelStyleStatusCard(
    status: ModuleStatus,
    monetEnabled: Boolean,
    safetyMode: Boolean,
) {
    val targetFace = when {
        status.checking -> StatusFace.CHECKING
        safetyMode -> StatusFace.SAFE_MODE
        status.active -> StatusFace.ACTIVE
        status.pendingRestart -> StatusFace.PENDING_RESTART
        else -> StatusFace.INACTIVE
    }
    var displayedFace by remember { mutableStateOf(targetFace) }
    val flip = remember { Animatable(0f) }
    LaunchedEffect(targetFace) {
        if (displayedFace != targetFace) {
            flip.snapTo(0f)
            flip.animateTo(90f, tween(220))
            displayedFace = targetFace
            flip.animateTo(180f, tween(220))
            flip.snapTo(0f)
        }
    }

    val angle = if (flip.value <= 90f) flip.value else flip.value - 180f
    val active = displayedFace == StatusFace.ACTIVE
    val pending = displayedFace == StatusFace.PENDING_RESTART
    val checking = displayedFace == StatusFace.CHECKING
    val safe = displayedFace == StatusFace.SAFE_MODE
    val dark = isInDarkTheme()
    val statusTextColor = MiuixTheme.colorScheme.onSurface
    val monetPalette = if (monetEnabled) {
        val primary = MiuixTheme.colorScheme.primary
        StatusPalette(
            container = primary.copy(alpha = if (dark) 0.34f else 0.17f)
                .compositeOver(MiuixTheme.colorScheme.surfaceContainerHigh),
            accent = primary,
            content = statusTextColor,
        )
    } else {
        null
    }
    val palette = when {
        monetPalette != null -> monetPalette
        active -> StatusPalette(
            container = if (dark) Color(0xFF1A3825) else Color(0xFFDFFAE4),
            accent = Color(0xFF208A48),
            content = statusTextColor,
        )
        pending || safe -> StatusPalette(
            container = if (dark) Color(0xFF3E2F1B) else Color(0xFFFFF0DB),
            accent = Color(0xFFB56B00),
            content = statusTextColor,
        )
        else -> StatusPalette(
            container = if (dark) HyperRedContainerDark else HyperRedContainerLight,
            accent = HyperRed,
            content = statusTextColor,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 168.dp).graphicsLayer {
            rotationY = angle
            cameraDistance = 12f * density
        },
        colors = CardDefaults.defaultColors(
            color = palette.container,
            contentColor = palette.content,
        ),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Icon(
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 18.dp, top = 18.dp).size(64.dp),
                imageVector = when {
                    active -> Icons.Rounded.CheckCircleOutline
                    pending -> Icons.Rounded.RemoveCircleOutline
                    checking -> Icons.Rounded.HourglassTop
                    safe -> Icons.Rounded.Shield
                    else -> Icons.Rounded.HighlightOff
                },
                tint = palette.accent.copy(alpha = 0.78f),
                contentDescription = null,
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                Column(Modifier.padding(end = 72.dp)) {
                    Text(
                        text = when {
                            active -> "工作中"
                            pending -> "待重启"
                            checking -> "检测中"
                            safe -> "安全模式"
                            else -> "未激活"
                        },
                        fontSize = 25.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = when {
                            active -> listOf(status.frameworkName, status.frameworkVersion)
                                .filter(String::isNotBlank).joinToString(" ").ifBlank { "LibXposed API 102" }
                            pending -> "模块已加载，重启目标后生效"
                            checking -> "正在读取模块和作用域状态"
                            safe -> "效果已暂停，可在设置中关闭安全模式"
                            else -> status.message
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.content.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (active) "API 102" else "Freeform Unbounded",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.content.copy(alpha = 0.72f),
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.content.copy(alpha = 0.72f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RestartPrimaryCard(
    onRestartSystemUi: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primaryContainer,
            contentColor = MiuixTheme.colorScheme.onPrimaryContainer,
        ),
        onClick = onRestartSystemUi,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(27.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("重启系统界面", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "刷新自由窗口功能 · 需要 ROOT",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
                )
            }
            Text("重启", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RestartTargetsPopup(
    show: Boolean,
    onShowChange: (Boolean) -> Unit,
    onRestartSystemUi: () -> Unit,
    onRestartAod: () -> Unit,
    onRestartMiWallpaper: () -> Unit,
    onRestartMoon: () -> Unit,
    onRestartSnowMountain: () -> Unit,
    onRestartGeometry: () -> Unit,
    onRestartSaturn: () -> Unit,
    onRestartEarth: () -> Unit,
    onRestartMars: () -> Unit,
    onRestartAll: () -> Unit,
) {
    val options = listOf(
        RestartMenuOption("系统界面", "刷新自由窗口功能", onRestartSystemUi),
        RestartMenuOption("息屏与锁屏编辑", "刷新玻璃时钟", onRestartAod),
        RestartMenuOption("MiWallpaper", "刷新超级壁纸服务", onRestartMiWallpaper),
        RestartMenuOption("月球超级壁纸", "仅刷新月球实例", onRestartMoon),
        RestartMenuOption("雪山超级壁纸", "仅刷新雪山实例", onRestartSnowMountain),
        RestartMenuOption("几何超级壁纸", "仅刷新几何实例", onRestartGeometry),
        RestartMenuOption("土星超级壁纸", "仅刷新土星实例", onRestartSaturn),
        RestartMenuOption("地球超级壁纸", "仅刷新地球实例", onRestartEarth),
        RestartMenuOption("火星超级壁纸", "仅刷新火星实例", onRestartMars),
        RestartMenuOption("全部静态作用域", "依次重启 9 个目标", onRestartAll),
    )
    Box {
        IconButton(
            onClick = { onShowChange(true) },
            holdDownState = show,
        ) {
            Icon(
                imageVector = Icons.Rounded.RestartAlt,
                contentDescription = "重启目标",
                tint = MiuixTheme.colorScheme.onBackground,
            )
        }
        OverlayListPopup(
            show = show,
            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            maxHeight = 560.dp,
            onDismissRequest = { onShowChange(false) },
        ) {
            ListPopupColumn {
                options.forEachIndexed { index, option ->
                    DropdownImpl(
                        item = DropdownItem(text = option.title, summary = option.summary),
                        optionSize = options.size,
                        isSelected = false,
                        index = index,
                        onSelectedIndexChange = {
                            onShowChange(false)
                            option.action()
                        },
                    )
                }
            }
        }
    }
}

private data class RestartMenuOption(
    val title: String,
    val summary: String,
    val action: () -> Unit,
)

@Composable
private fun FeatureStatusCard(
    freeformEnabled: Boolean,
    glassClockEnabled: Boolean,
    superWallpaperDepthEnabled: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        FeatureStatusRow(
            icon = Icons.Rounded.Security,
            title = "自由窗口边界保护",
            enabled = freeformEnabled,
            summary = if (freeformEnabled) "已开启 · 重启系统界面后生效" else "已关闭 · 保留系统原行为",
        )
        FeatureStatusRow(
            icon = Icons.Rounded.Wallpaper,
            title = "强制使用玻璃时钟",
            enabled = glassClockEnabled,
            summary = if (glassClockEnabled) "已开启 · 修改后重启息屏与锁屏编辑" else "已关闭 · 保留系统原有时钟限制",
        )
        FeatureStatusRow(
            icon = Icons.Rounded.Layers,
            title = "超级壁纸景深",
            enabled = superWallpaperDepthEnabled,
            summary = if (superWallpaperDepthEnabled) "已开启 · 修改后重启系统界面" else "已关闭 · 保留超级壁纸原有行为",
        )
    }
}

@Composable
private fun FeatureStatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean,
    summary: String,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
        },
        endActions = {
            Text(
                text = if (enabled) "开启" else "关闭",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        },
    )
}

@Composable
private fun DeviceInfoCard() {
    val kernelVersion = System.getProperty("os.version").orEmpty().ifBlank { "未知" }
    Card(modifier = Modifier.fillMaxWidth()) {
        DeviceInfoRow(
            icon = Icons.Rounded.Smartphone,
            title = "设备",
            summary = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(" ")
                .ifBlank { "未知设备" },
        )
        DeviceInfoRow(
            icon = Icons.Rounded.Android,
            title = "Android",
            summary = "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
        )
        DeviceInfoRow(
            icon = Icons.Rounded.DeveloperBoard,
            title = "系统版本",
            summary = Build.DISPLAY.ifBlank { "未知" },
        )
        DeviceInfoRow(
            icon = Icons.Rounded.Info,
            title = "Linux 内核",
            summary = kernelVersion,
        )
    }
}

@Composable
private fun DeviceInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        },
    )
}

@Composable
private fun DiagnosticsCard(
    status: String?,
    exporting: Boolean,
    onExport: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = "景深诊断日志",
            summary = status ?: "导出设备信息、logcat 与关键服务状态",
            startAction = {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp),
                    tint = MiuixTheme.colorScheme.primary,
                )
            },
            endActions = {
                TextButton(
                    text = if (exporting) "读取中" else "导出",
                    onClick = onExport,
                    enabled = !exporting,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            },
        )
    }
}

private enum class StatusFace { CHECKING, ACTIVE, PENDING_RESTART, INACTIVE, SAFE_MODE }

private data class StatusPalette(
    val container: Color,
    val accent: Color,
    val content: Color,
)
