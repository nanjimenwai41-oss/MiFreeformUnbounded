package com.freeform.unbounded.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.HighlightOff
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeform.unbounded.BuildConfig
import com.freeform.unbounded.ModuleStatus
import com.freeform.unbounded.ModuleStatusRepository
import com.freeform.unbounded.SystemUiRestarter
import com.freeform.unbounded.ui.theme.HyperGreenContainerDark
import com.freeform.unbounded.ui.theme.HyperGreenContainerLight
import com.freeform.unbounded.ui.theme.HyperRed
import com.freeform.unbounded.ui.theme.HyperRedContainerDark
import com.freeform.unbounded.ui.theme.HyperRedContainerLight
import com.freeform.unbounded.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlinx.coroutines.launch

@Composable
internal fun HomeScreen(enableBlur: Boolean) {
    val status by ModuleStatusRepository.status.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(
        topBar = {
            BlurredBar(blurBackdrop) {
                TopAppBar(
                    color = barColor,
                    title = "首页",
                    largeTitle = "Freeform Unbounded",
                )
            }
        },
    ) { padding ->
        Box(
            modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { KernelStyleStatusCard(status) }
                item {
                    RuntimeCard(
                        status = status,
                        onRestartSystemUi = {
                            scope.launch { SystemUiRestarter.restart(context) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun KernelStyleStatusCard(status: ModuleStatus) {
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
    val statusTextColor = if (isInDarkTheme()) Color.White else Color.Black

    // Status cards intentionally use fixed semantic colors. Monet is not applied
    // to any of the three faces, matching the requested KernelSU-style card look.
    val workingPalette = StatusPalette(
        container = if (isInDarkTheme()) HyperGreenContainerDark else HyperGreenContainerLight,
        accent = ActiveAccent,
        content = statusTextColor,
    )

    // Warning glyphs keep their original yellow/red accents while text stays neutral.
    val pendingPalette = StatusPalette(
        container = if (isInDarkTheme()) PendingContainerDark else PendingContainerLight,
        accent = PendingAccent,
        content = statusTextColor,
    )
    val inactivePalette = StatusPalette(
        container = if (isInDarkTheme()) HyperRedContainerDark else HyperRedContainerLight,
        accent = HyperRed,
        content = statusTextColor,
    )
    val palette = when {
        active -> workingPalette
        pending -> pendingPalette
        else -> inactivePalette
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(156.dp).graphicsLayer {
            rotationY = angle
            cameraDistance = 12f * density
        },
        colors = CardDefaults.defaultColors(
            color = palette.container,
            contentColor = palette.content,
        ),
        onClick = {},
        showIndication = true,
        pressFeedbackType = if (inactive) PressFeedbackType.Sink else PressFeedbackType.Tilt,
    ) {
        when {
            active -> KernelStyleActiveFace(status, palette.accent)
            pending -> KernelStylePendingFace(status, palette.accent)
            else -> KernelStyleInactiveFace(status, palette.accent)
        }
    }
}

/** Mirrors KernelSU's active Miuix home card: green container, oversized check outline,
 * 16.dp typography rhythm, and the same bottom-left mode/version label. */
@Composable
private fun KernelStyleActiveFace(status: ModuleStatus, accent: Color) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(27.dp, 31.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Icon(
                modifier = Modifier.size(110.dp),
                imageVector = Icons.Rounded.CheckCircleOutline,
                tint = accent,
                contentDescription = null,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text("工作中", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                text = listOf(status.frameworkName, status.frameworkVersion)
                    .filter(String::isNotBlank)
                    .joinToString(" ")
                    .ifBlank { "LibXposed API 102" },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
        }
        Text(
            text = "API 102",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** KernelSU has no pending-reboot state; this keeps its card geometry and uses the
 * rounded circle-minus glyph with KernelSU's Notice yellow palette. */
@Composable
private fun KernelStylePendingFace(status: ModuleStatus, accent: Color) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(27.dp, 31.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Icon(
                modifier = Modifier.size(110.dp),
                imageVector = Icons.Rounded.RemoveCircleOutline,
                tint = accent,
                contentDescription = null,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text("待重启", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "模块已安装，点击下方重启系统界面",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
        }
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** The inactive face mirrors the active KernelSU card exactly, changing only its
 * container, semantic color, title, and rounded circle-cross glyph. */
@Composable
private fun KernelStyleInactiveFace(status: ModuleStatus, accent: Color) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(27.dp, 31.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Icon(
                modifier = Modifier.size(110.dp),
                imageVector = Icons.Rounded.HighlightOff,
                tint = accent,
                contentDescription = null,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = if (status.checking) "检测中" else "未激活",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (status.checking) "正在读取当前目标进程状态" else status.message,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
        }
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RuntimeCard(
    status: ModuleStatus,
    onRestartSystemUi: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = "重启系统界面",
            summary = "重启后应用新的边缘距离\n需要 ROOT 权限",
            endActions = {
                TextButton(
                    text = "重启",
                    onClick = onRestartSystemUi,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            },
        )
        BasicComponent(title = "边缘保护", summary = "全部应用 · 始终开启")
    }
}

private enum class StatusFace { CHECKING, ACTIVE, PENDING_RESTART, INACTIVE }

private data class StatusPalette(
    val container: Color,
    val accent: Color,
    val content: Color,
)

private val ActiveAccent = Color(0xFF36D167)
private val PendingAccent = Color(0xFFF5A623)
private val PendingContainerLight = Color(0xFFFFF0DB)
private val PendingContainerDark = Color(0xFF3E2F1B)
