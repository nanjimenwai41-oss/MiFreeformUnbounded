package com.freeform.unbounded.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.ui.res.painterResource
import com.freeform.unbounded.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeform.unbounded.BuildConfig
import com.freeform.unbounded.ModuleStatus
import com.freeform.unbounded.ModuleStatusRepository
import com.freeform.unbounded.SystemUiRestarter
import com.freeform.unbounded.ui.theme.HyperRed
import com.freeform.unbounded.ui.theme.HyperRedContainerDark
import com.freeform.unbounded.ui.theme.HyperRedContainerLight
import com.freeform.unbounded.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
internal fun HomeScreen(
    enableBlur: Boolean,
    contentBottomPadding: Dp,
    monetEnabled: Boolean,
) {
    val status by ModuleStatusRepository.status.collectAsState()
    val context = LocalContext.current
    var showRestartMenu by rememberSaveable { mutableStateOf(false) }
    val deviceInfo by rememberDeviceInfo(showRestartMenu)
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
                        IconButton(
                            onClick = { showRestartMenu = true },
                            holdDownState = showRestartMenu,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_restart),
                                contentDescription = "重启目标",
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
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
                    )
                }
                item { DeviceInfoCard(deviceInfo) }
            }
        }
        RestartTargetsDialog(
            show = showRestartMenu,
            currentWallpaper = deviceInfo?.currentWallpaper,
            onDismiss = { showRestartMenu = false },
            onRestart = { selected ->
                showRestartMenu = false
                SystemUiRestarter.restartSelected(context, selected)
            },
        )
    }
}

@Composable
private fun KernelStyleStatusCard(status: ModuleStatus, monetEnabled: Boolean) {
    val targetFace = when {
        status.checking -> StatusFace.CHECKING
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
    val inactive = !active && !pending
    val dark = isInDarkTheme()
    val colors = MiuixTheme.colorScheme
    val monetPalette = if (monetEnabled) {
        StatusPalette(
            container = colors.primary.copy(alpha = if (dark) 0.36f else 0.16f)
                .compositeOver(colors.surfaceContainerHigh),
            accent = colors.primary,
            content = colors.onSurface,
        )
    } else null
    val palette = monetPalette ?: when {
        active -> StatusPalette(
            container = if (dark) Color(0xFF1A3825) else Color(0xFFDFFAE4),
            accent = if (dark) Color(0xFF8DE7A5) else Color(0xFF208A48),
            content = colors.onSurface,
        )
        pending -> StatusPalette(
            container = if (dark) Color(0xFF3E2F1B) else Color(0xFFFFF0DB),
            accent = if (dark) Color(0xFFFFC56B) else Color(0xFFB56B00),
            content = colors.onSurface,
        )
        else -> StatusPalette(
            container = if (dark) HyperRedContainerDark else HyperRedContainerLight,
            accent = HyperRed,
            content = colors.onSurface,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(156.dp).graphicsLayer {
            rotationY = angle
            cameraDistance = 12f * density
        },
        colors = CardDefaults.defaultColors(color = palette.container, contentColor = palette.content),
        onClick = {},
        showIndication = true,
        pressFeedbackType = if (inactive) PressFeedbackType.Sink else PressFeedbackType.Tilt,
    ) {
        when {
            active -> KernelStyleActiveFace(status, palette.accent, palette.content)
            pending -> KernelStylePendingFace(palette.accent, palette.content)
            else -> KernelStyleInactiveFace(status, displayedFace == StatusFace.CHECKING, palette.accent, palette.content)
        }
    }
}

@Composable
private fun KernelStyleActiveFace(status: ModuleStatus, accent: Color, content: Color) {
    StatusFaceLayout(
        title = "已激活",
        summary = listOf(status.frameworkName, status.frameworkVersion).filter(String::isNotBlank)
            .joinToString(" ").ifBlank { "LibXposed API 102" },
        footer = "API 102",
        icon = Icons.Rounded.CheckCircleOutline,
        accent = accent,
        content = content,
    )
}

@Composable
private fun KernelStylePendingFace(accent: Color, content: Color) {
    StatusFaceLayout(
        title = "待重启",
        summary = "模块已安装，请按需重启系统界面或息屏与锁屏编辑",
        footer = "v${BuildConfig.VERSION_NAME}",
        icon = Icons.Rounded.RemoveCircleOutline,
        accent = accent,
        content = content,
    )
}

@Composable
private fun KernelStyleInactiveFace(status: ModuleStatus, checking: Boolean, accent: Color, content: Color) {
    StatusFaceLayout(
        title = if (checking) "检测中" else "未激活",
        summary = if (checking) "正在读取当前目标进程状态" else status.message,
        footer = "v${BuildConfig.VERSION_NAME}",
        icon = Icons.Rounded.HighlightOff,
        accent = accent,
        content = content,
    )
}

@Composable
private fun StatusFaceLayout(
    title: String,
    summary: String,
    footer: String,
    icon: ImageVector,
    accent: Color,
    content: Color,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(110.dp).offset(x = 27.dp, y = 31.dp), tint = accent)
        }
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(title, color = content, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                summary,
                color = content,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            footer,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 12.dp),
            color = content,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private enum class StatusFace { CHECKING, ACTIVE, PENDING_RESTART, INACTIVE }

private data class StatusPalette(
    val container: Color,
    val accent: Color,
    val content: Color,
)
