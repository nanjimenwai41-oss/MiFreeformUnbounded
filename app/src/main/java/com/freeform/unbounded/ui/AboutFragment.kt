package com.freeform.unbounded.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.freeform.unbounded.BuildConfig
import com.freeform.unbounded.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val REPOSITORY_URL = "https://github.com/nanjimenwai41-oss/MiFreeformUnbounded"
private const val MIUIX_URL = "https://github.com/compose-miuix-ui/miuix"
private const val KERNELSU_URL = "https://github.com/tiann/KernelSU"
private const val HYPERLIGHT_URL = "https://github.com/KiminonawaResa/HyperLight"

@Composable
internal fun AboutModuleInfo(
    enableBlur: Boolean,
    contentBottomPadding: Dp,
    onOpenAbout: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    Scaffold(topBar = {
        BlurredBar(blurBackdrop) {
            TopAppBar(
                color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                title = "模块说明",
                largeTitle = "模块说明",
                scrollBehavior = scrollBehavior,
            )
        }
    }) { padding ->
        Box(if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
                    .scrollEndHaptic().overScrollVertical(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = 12.dp,
                    bottom = contentBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
                item {
                    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
                        Text("Freeform Unbounded", style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
                        Text(
                            "为 HyperOS 提供自由窗口边界保护、锁屏玻璃时钟和超级壁纸景深效果。模块自动管理静态作用域；修改功能后可在首页选择并重启对应进程。",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        ArrowPreference(
                            title = "关于与鸣谢",
                            summary = "版本信息、开源协议和项目链接",
                            startAction = { Icon(Icons.Rounded.Info, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
                            onClick = onOpenAbout,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AboutScreen(
    enableBlur: Boolean,
    contentBottomPadding: Dp,
    onBack: () -> Unit,
) {
    val state = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val colors = MiuixTheme.colorScheme
    val progress by remember {
        androidx.compose.runtime.derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / with(density) { 340.dp.toPx() }).coerceIn(0f, 1f)
        }
    }
    val barBackdrop = rememberBlurBackdrop(enableBlur)
    val flowBackdrop = top.yukonga.miuix.kmp.blur.rememberLayerBackdrop()
    val flowEnabled = enableBlur && top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported()
    Scaffold(
        topBar = {
            BlurredBar(barBackdrop, blurActive = progress >= 1f) {
                top.yukonga.miuix.kmp.basic.SmallTopAppBar(
                    title = "关于",
                    titleColor = colors.onSurface.copy(alpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)),
                    color = if (progress < 1f || barBackdrop != null) Color.Transparent else colors.surface,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = colors.primary)
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(if (barBackdrop != null) Modifier.layerBackdrop(barBackdrop) else Modifier) {
            com.freeform.unbounded.ui.component.effect.BgEffectBackground(
                dynamicBackground = flowEnabled && progress < 1f,
                modifier = Modifier.fillMaxSize(),
                bgModifier = Modifier.layerBackdrop(flowBackdrop),
                effectBackground = flowEnabled,
                alpha = { 1f - progress },
            ) {
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = padding.calculateTopPadding(),
                        end = 12.dp,
                        bottom = contentBottomPadding,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    overscrollEffect = null,
                ) {
                    item(key = "hero") {
                        AboutHero(progress, flowBackdrop, flowEnabled)
                    }
                    item(key = "links") {
                        Column(Modifier.fillParentMaxHeight()) {
                            AboutLinks()
                            Text(
                                "GNU AGPL v3.0",
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceVariantSummary,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutLinks() {
    val uri = LocalUriHandler.current
    Card(Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = "GitHub 仓库",
            summary = "源码、版本与问题反馈",
            startAction = { Icon(Icons.AutoMirrored.Rounded.Launch, null, Modifier.padding(end = 14.dp), tint = MiuixTheme.colorScheme.primary) },
            onClick = { uri.openUri(REPOSITORY_URL) },
        )
        ArrowPreference(
            title = "此应用基于 MIUIX",
            summary = "MIUIX 0.9.4 · Compose 组件与主题系统",
            startAction = { Icon(Icons.Rounded.DesignServices, null, Modifier.padding(end = 14.dp), tint = MiuixTheme.colorScheme.primary) },
            onClick = { uri.openUri(MIUIX_URL) },
        )
        ArrowPreference(
            title = "参考 KernelSU 管理器",
            summary = "界面布局、主题预览、液态底栏与关于页流光效果",
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

/** 参照 KernelSU 居中徽标、流光遮罩及分段滚动淡出的布局。 */
@Composable
private fun AboutHero(
    progress: Float,
    backdrop: top.yukonga.miuix.kmp.blur.LayerBackdrop,
    flowEnabled: Boolean,
) {
    val colors = MiuixTheme.colorScheme
    val flow = if (flowEnabled) Modifier.textureBlur(
        backdrop = backdrop,
        shape = androidx.compose.ui.graphics.RectangleShape,
        blurRadius = 60f,
        colors = top.yukonga.miuix.kmp.blur.BlurColors(
            blendColors = listOf(top.yukonga.miuix.kmp.blur.BlendColorEntry(colors.primary.copy(alpha = 0.65f))),
        ),
        contentBlendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
    ) else Modifier
    fun Modifier.fade(start: Float, end: Float): Modifier = graphicsLayer {
        val fade = ((progress - start) / (end - start)).coerceIn(0f, 1f)
        alpha = 1f - fade
        scaleX = 1f - fade * 0.05f
        scaleY = scaleX
        translationY = -fade * 12.dp.toPx()
    }
    Column(
        modifier = Modifier.fillMaxWidth().height(400.dp).padding(top = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(colors.primary),
            modifier = Modifier.size(130.dp).fade(0.55f, 1f).then(flow),
        )
        Text(
            text = "Freeform Unbounded",
            color = colors.onBackground,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fade(0.3f, 0.85f).then(flow),
        )
        Text(
            text = "${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}",
            color = colors.onSurfaceVariantSummary,
            fontSize = 14.sp,
            modifier = Modifier.fade(0f, 0.55f),
        )
    }
}
