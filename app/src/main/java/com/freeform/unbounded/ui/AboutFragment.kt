package com.freeform.unbounded.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
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
internal fun AboutScreen(
    enableBlur: Boolean,
    contentBottomPadding: Dp,
) {
    val uri = LocalUriHandler.current
    val state = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(
        topBar = {
            BlurredBar(blurBackdrop) {
                TopAppBar(
                    color = barColor,
                    title = "关于",
                    largeTitle = "关于",
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier,
        ) {
            LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = padding.calculateTopPadding() + 16.dp,
                end = 12.dp,
                bottom = contentBottomPadding,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            overscrollEffect = null,
        ) {
            item {
                AboutHero()
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(18.dp),
                ) {
                    Text(
                        "模块说明",
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "本模块在系统界面、息屏与锁屏编辑和 MiWallpaper 的静态作用域中工作：提供自由小窗边界保护、超级壁纸景深和锁屏玻璃时钟功能。静态作用域由模块声明自动管理，无需在 LSPosed 中维护推荐应用列表。修改功能后可在首页分别重启目标，或重启全部静态作用域；重启操作需要 ROOT 权限。",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
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
                        summary = "MIUIX 0.9.4 · Compose 组件与主题系统",
                        startAction = { Icon(Icons.Rounded.DesignServices, null, Modifier.padding(end = 14.dp), tint = MiuixTheme.colorScheme.primary) },
                        onClick = { uri.openUri(MIUIX_URL) },
                    )
                    ArrowPreference(
                        title = "参考 KernelSU 管理器",
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
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(colors.primaryContainer)
            .padding(vertical = 22.dp, horizontal = 18.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_app_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .squircleClip(24.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Freeform Unbounded",
                    color = colors.onPrimaryContainer,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${BuildConfig.VERSION_NAME} · ${BuildConfig.VERSION_CODE}",
                    color = colors.onPrimaryContainer.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                )
                Text(
                    "HyperOS 自由窗口与锁屏效果模块",
                    color = colors.onPrimaryContainer.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}
