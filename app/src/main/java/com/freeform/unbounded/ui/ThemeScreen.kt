package com.freeform.unbounded.ui

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.preference.ArrowPreference
import androidx.compose.ui.unit.dp
import com.freeform.unbounded.AppSettings
import com.freeform.unbounded.AppSettingsRepository
import com.freeform.unbounded.DarkMode
import com.freeform.unbounded.FreeformApplication
import com.freeform.unbounded.DEFAULT_MAX_PREDICTIVE_BACK_PROGRESS
import com.freeform.unbounded.MAX_MAX_PREDICTIVE_BACK_PROGRESS
import com.freeform.unbounded.MIN_MAX_PREDICTIVE_BACK_PROGRESS
import com.freeform.unbounded.MIN_PAGE_SCALE
import com.freeform.unbounded.MAX_PAGE_SCALE
import com.freeform.unbounded.ui.theme.effectiveFor
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlin.math.roundToInt

@Composable
internal fun ThemeScreen(
    settings: AppSettings,
    contentBottomPadding: Dp,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scrollBehavior = MiuixScrollBehavior()
    val darkIndex = DarkMode.entries.indexOf(settings.darkMode).coerceAtLeast(0)
    val blurBackdrop = rememberBlurBackdrop(settings.enableBlur)
    val barColor = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(
        topBar = {
            BlurredBar(blurBackdrop) {
                TopAppBar(
                    color = barColor,
                    title = "外观",
                    largeTitle = "外观",
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = MiuixTheme.colorScheme.primary)
                        }
                    },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars
            .add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { padding ->
        val state = rememberLazyListState()
        Box(
            modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier,
        ) {
            LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().scrollEndHaptic().overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            overscrollEffect = null,
            contentPadding = PaddingValues(
                12.dp, padding.calculateTopPadding() + 8.dp, 12.dp,
                contentBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ThemePreviewCard(settings) }
            item {
                TabRow(
                    tabs = listOf("跟随系统", "浅色", "深色", "纯黑"),
                    selectedTabIndex = darkIndex,
                    onTabSelected = { index ->
                        AppSettingsRepository.setDarkMode(DarkMode.entries[index.coerceIn(0, DarkMode.entries.lastIndex)])
                    },
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "启用 Monet 颜色",
                        summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "使用系统壁纸动态颜色" else "当前系统不支持动态取色",
                        startAction = { Icon(Icons.Rounded.Wallpaper, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
                        checked = settings.monetEnabled,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                        onCheckedChange = AppSettingsRepository::setMonetEnabled,
                    )
                    AnimatedVisibility(visible = settings.monetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Column {
                            OverlayDropdownPreference(
                                title = "强调色",
                                summary = keyColorLabels[keyColorValues.indexOf(settings.seedColor).coerceAtLeast(0)],
                                startAction = { Icon(Icons.Rounded.Colorize, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
                                items = keyColorLabels,
                                selectedIndex = keyColorValues.indexOf(settings.seedColor).coerceAtLeast(0),
                                onSelectedIndexChange = { AppSettingsRepository.setSeedColor(keyColorValues[it]) },
                            )
                            OverlayDropdownPreference(
                                title = "色彩风格",
                                enabled = settings.seedColor != AppSettings.DEFAULT_SEED_COLOR,
                                summary = if (settings.seedColor == AppSettings.DEFAULT_SEED_COLOR) "跟随系统壁纸色板" else settings.paletteStyle.displayName(),
                                startAction = { Icon(Icons.Rounded.Style, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
                                items = ThemePaletteStyle.entries.map { it.displayName() },
                                selectedIndex = ThemePaletteStyle.entries.indexOf(settings.paletteStyle).coerceAtLeast(0),
                                onSelectedIndexChange = { AppSettingsRepository.setPaletteStyle(ThemePaletteStyle.entries[it]) },
                            )
                            val specs = ThemeColorSpec.entries
                            OverlayDropdownPreference(
                                title = "色彩规范",
                                enabled = settings.seedColor != AppSettings.DEFAULT_SEED_COLOR,
                                summary = if (settings.seedColor == AppSettings.DEFAULT_SEED_COLOR) {
                                    "由系统动态颜色提供"
                                } else if (settings.colorSpec == settings.colorSpec.effectiveFor(settings.paletteStyle)) {
                                    settings.colorSpec.displayName()
                                } else {
                                    "当前风格使用 Material 2021"
                                },
                                startAction = { Icon(Icons.Rounded.DesignServices, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
                                items = specs.map { it.displayName() },
                                selectedIndex = specs.indexOf(settings.colorSpec).coerceAtLeast(0),
                                onSelectedIndexChange = { AppSettingsRepository.setColorSpec(specs[it]) },
                            )
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        SwitchPreference(
                            title = "模糊",
                            summary = "启用顶栏和底栏的模糊效果",
                            startAction = { Icon(Icons.Rounded.BlurOn, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
                            checked = settings.enableBlur,
                            onCheckedChange = AppSettingsRepository::setEnableBlur,
                        )
                    }
                    SwitchPreference(
                        title = "悬浮底栏",
                        summary = "使用 KernelSU 风格的悬浮底栏",
                        startAction = { Icon(Icons.Rounded.CallToAction, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
                        checked = settings.floatingBottomBar,
                        onCheckedChange = AppSettingsRepository::setFloatingBottomBar,
                    )
                    AnimatedVisibility(
                        visible = settings.enableBlur && settings.floatingBottomBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                    ) {
                        SwitchPreference(
                            title = "液态玻璃",
                            summary = "启用悬浮底栏的液态玻璃效果",
                            startAction = { Icon(Icons.Rounded.WaterDrop, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
                            checked = settings.floatingBottomBarBlur,
                            onCheckedChange = AppSettingsRepository::setFloatingBottomBarBlur,
                        )
                    }
                    SwitchPreference(
                        title = "导航栏角标",
                        summary = "在导航栏显示激活状态提示",
                        startAction = { Icon(Icons.Rounded.Notifications, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
                        checked = settings.navigationBadge,
                        onCheckedChange = AppSettingsRepository::setNavigationBadge,
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "滑动返回",
                        summary = "向右滑动返回上一级页面",
                        startAction = { Icon(Icons.Rounded.Swipe, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
                        checked = settings.swipeDismiss,
                        onCheckedChange = AppSettingsRepository::setSwipeDismiss,
                    )
                    OverlayDropdownPreference(
                        title = "翻页手势",
                        summary = when (settings.pagerGestureMode) {
                            PagerInterceptionMode.Native -> "使用 Compose 标准手势"
                            PagerInterceptionMode.CrossAxisInterceptor -> "横向滑动可接管列表惯性滚动"
                            PagerInterceptionMode.TapToHalt -> "列表滚动时，第一次滑动仅停止滚动"
                        },
                        startAction = { Icon(Icons.Rounded.ViewCarousel, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
                        items = listOf("原生", "跨轴接管", "iOS 风格"),
                        selectedIndex = settings.pagerGestureMode.ordinal,
                        onSelectedIndexChange = { AppSettingsRepository.setPagerGestureMode(PagerInterceptionMode.entries[it]) },
                    )
                    var scale by remember(settings.pageScale) { mutableFloatStateOf(settings.pageScale) }
                    BasicComponent(
                        title = "页面缩放",
                        summary = "${(scale * 100).roundToInt()}% · 调整整个应用的界面大小",
                        startAction = { Icon(Icons.Rounded.AspectRatio, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
                        endActions = {
                            TextButton(text = "重置", enabled = scale != 1f, onClick = {
                                scale = 1f
                                AppSettingsRepository.setPageScale(1f)
                            })
                        },
                        bottomAction = {
                            Slider(
                                value = scale,
                                onValueChange = { scale = it },
                                onValueChangeFinished = { AppSettingsRepository.setPageScale(scale) },
                                valueRange = MIN_PAGE_SCALE..MAX_PAGE_SCALE,
                                showKeyPoints = true,
                                keyPoints = listOf(0.8f, 0.9f, 1f, 1.1f),
                                magnetThreshold = 0.01f,
                                hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                            )
                        },
                    )
                }
            }
            item {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        SwitchPreference(
                            title = "预测性返回手势",
                            summary = "启用对预测性返回手势的支持。",
                            startAction = { Icon(Icons.AutoMirrored.Rounded.MenuOpen, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.primary) },
                            checked = settings.predictiveBack,
                            onCheckedChange = {
                                AppSettingsRepository.setPredictiveBack(it)
                                FreeformApplication.setEnableOnBackInvokedCallback(
                                    context.applicationInfo,
                                    it,
                                )
                                activity?.recreate()
                            },
                        )
                        AnimatedVisibility(visible = settings.predictiveBack) {
                            var sliderValue by remember(settings.maxPredictiveBackProgress) {
                                mutableFloatStateOf(settings.maxPredictiveBackProgress)
                            }
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("最大预测返回进度", fontWeight = FontWeight.Medium)
                                        Text(
                                            "控制返回手势预览的最大页面位移",
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                    Text(
                                        text = "${(sliderValue * 100).roundToInt()}%",
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                Slider(
                                    value = sliderValue,
                                    onValueChange = { sliderValue = it },
                                    onValueChangeFinished = {
                                        AppSettingsRepository.setMaxPredictiveBackProgress(sliderValue)
                                    },
                                    valueRange = MIN_MAX_PREDICTIVE_BACK_PROGRESS..MAX_MAX_PREDICTIVE_BACK_PROGRESS,
                                    showKeyPoints = true,
                                    keyPoints = listOf(
                                        MIN_MAX_PREDICTIVE_BACK_PROGRESS,
                                        DEFAULT_MAX_PREDICTIVE_BACK_PROGRESS,
                                        MAX_MAX_PREDICTIVE_BACK_PROGRESS,
                                    ),
                                    magnetThreshold = 0.01f,
                                    hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}


private fun ThemePaletteStyle.displayName(): String = when (this) {
    ThemePaletteStyle.TonalSpot -> "Tonal Spot"
    ThemePaletteStyle.Neutral -> "Neutral"
    ThemePaletteStyle.Vibrant -> "Vibrant"
    ThemePaletteStyle.Expressive -> "Expressive"
    ThemePaletteStyle.Fidelity -> "Fidelity"
    ThemePaletteStyle.Content -> "Content"
    ThemePaletteStyle.Monochrome -> "Monochrome"
    ThemePaletteStyle.Rainbow -> "Rainbow"
    ThemePaletteStyle.FruitSalad -> "Fruit Salad"
}

private fun ThemeColorSpec.displayName(): String = when (this) {
    ThemeColorSpec.Spec2021 -> "Material 2021"
    ThemeColorSpec.Spec2025 -> "Material 2025"
}
