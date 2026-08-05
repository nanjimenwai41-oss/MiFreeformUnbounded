package com.freeform.unbounded.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeform.unbounded.BuildConfig
import com.freeform.unbounded.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val REPOSITORY_URL = "https://github.com/nanjimenwai41-oss/MiFreeformUnbounded"
private const val MIUIX_URL = "https://github.com/compose-miuix-ui/miuix"
private const val KERNELSU_URL = "https://github.com/tiann/KernelSU"
private const val HYPERLIGHT_URL = "https://github.com/KiminonawaResa/HyperLight"

@Composable
internal fun AboutScreen(enableBlur: Boolean) {
    val uri = LocalUriHandler.current
    val state = rememberLazyListState()
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(
        topBar = {
            BlurredBar(blurBackdrop) {
                SmallTopAppBar(
                    color = barColor,
                    title = "关于",
                )
            }
        },
    ) { padding ->
        Box(
            modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier,
        ) {
            LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 16.dp, 16.dp, 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            overscrollEffect = null,
        ) {
            item {
                AboutHero()
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "GitHub 仓库",
                        summary = "源码、版本与问题反馈",
                        startAction = { Icon(Icons.AutoMirrored.Rounded.Launch, null, Modifier.padding(end = 14.dp), tint = MiuixTheme.colorScheme.primary) },
                        onClick = { uri.openUri(REPOSITORY_URL) },
                    )
                    ArrowPreference(
                        title = "此应用基于 MIUIX",
                        summary = "界面组件、主题系统与 Navigation3 转场由 MIUIX 提供",
                        startAction = { Icon(Icons.Rounded.DesignServices, null, Modifier.padding(end = 14.dp), tint = MiuixTheme.colorScheme.primary) },
                        onClick = { uri.openUri(MIUIX_URL) },
                    )
                    ArrowPreference(
                        title = "参考 KernelSU",
                        summary = "主题设置、悬浮液态底栏与导航结构参考 KernelSU 管理器",
                        startAction = { Icon(Icons.Rounded.Layers, null, Modifier.padding(end = 14.dp), tint = MiuixTheme.colorScheme.primary) },
                        onClick = { uri.openUri(KERNELSU_URL) },
                    )
                    ArrowPreference(
                        title = "参考 HyperLight",
                        summary = "精细拖动交互思路参考 HyperLight；当前项目为独立实现",
                        startAction = { Icon(Icons.Rounded.Tune, null, Modifier.padding(end = 14.dp), tint = MiuixTheme.colorScheme.primary) },
                        onClick = { uri.openUri(HYPERLIGHT_URL) },
                    )
                }
            }
            item {
                Spacer(Modifier.height(24.dp))
                Text("GNU AGPL v3.0", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            }
        }
    }
}

@Composable
private fun AboutHero() {
    val transition = rememberInfiniteTransition(label = "about-light")
    val shift by transition.animateFloat(
        initialValue = -0.15f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(4800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "about-light-shift",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1D123B), Color(0xFF3C2A75), Color(0xFF112F61)),
                        start = Offset(size.width * shift, 0f),
                        end = Offset(size.width * (shift + 0.9f), size.height),
                    ),
                )
                drawCircle(Color(0xFFB38CFF).copy(alpha = 0.24f), radius = size.minDimension * 0.42f, center = Offset(size.width * (0.18f + shift * 0.2f), size.height * 0.22f))
                drawCircle(Color(0xFF5BD5FF).copy(alpha = 0.18f), radius = size.minDimension * 0.55f, center = Offset(size.width * (0.92f - shift * 0.25f), size.height * 0.82f))
            }
            .padding(vertical = 34.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_app_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(108.dp)
                    .squircleClip(36.dp),
            )
            Text("Freeform Unbounded", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Text("HyperOS 3 自由小窗边界模块", color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
        }
    }
    Spacer(Modifier.height(16.dp))
}
