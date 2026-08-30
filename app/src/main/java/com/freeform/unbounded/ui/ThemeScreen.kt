package com.freeform.unbounded.ui

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeform.unbounded.AppSettings
import com.freeform.unbounded.AppSettingsRepository
import com.freeform.unbounded.DarkMode
import com.freeform.unbounded.FreeformApplication
import com.freeform.unbounded.DEFAULT_MAX_PREDICTIVE_BACK_PROGRESS
import com.freeform.unbounded.MAX_MAX_PREDICTIVE_BACK_PROGRESS
import com.freeform.unbounded.MIN_MAX_PREDICTIVE_BACK_PROGRESS
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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import kotlin.math.roundToInt

@Composable
internal fun ThemeScreen(
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val darkIndex = when (settings.darkMode) {
        DarkMode.SYSTEM -> 0
        DarkMode.LIGHT -> 1
        DarkMode.DARK, DarkMode.AMOLED -> 2
    }
    val blurBackdrop = rememberBlurBackdrop(settings.enableBlur)
    val barColor = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    Scaffold(
        topBar = {
            BlurredBar(blurBackdrop) {
                TopAppBar(
                    color = barColor,
                    title = "主题设置",
                    largeTitle = "主题设置",
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp, padding.calculateTopPadding() + 8.dp, 12.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ThemePreviewCard(settings) }
            item {
                TabRow(
                    tabs = listOf("跟随系统", "浅色", "深色"),
                    selectedTabIndex = darkIndex,
                    onTabSelected = { index ->
                        AppSettingsRepository.setDarkMode(DarkMode.entries[index.coerceIn(0, 2)])
                    },
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "启用 Monet 颜色",
                        summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "使用系统壁纸动态颜色" else "当前系统不支持动态取色",
                        startAction = { Icon(Icons.Rounded.Wallpaper, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.onBackground) },
                        checked = settings.monetEnabled,
                        onCheckedChange = AppSettingsRepository::setMonetEnabled,
                    )
                    AnimatedVisibility(visible = settings.monetEnabled) {
                        Column {
                            OverlayDropdownPreference(
                                title = "强调色",
                                summary = keyColorLabels[keyColorValues.indexOf(settings.seedColor).coerceAtLeast(0)],
                                startAction = { Icon(Icons.Rounded.Colorize, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.onBackground) },
                                items = keyColorLabels,
                                selectedIndex = keyColorValues.indexOf(settings.seedColor).coerceAtLeast(0),
                                onSelectedIndexChange = { AppSettingsRepository.setSeedColor(keyColorValues[it]) },
                            )
                            AnimatedVisibility(visible = settings.seedColor != AppSettings.DEFAULT_SEED_COLOR) {
                                Column {
                                    OverlayDropdownPreference(
                                        title = "色彩风格",
                                        summary = settings.paletteStyle.displayName(),
                                        startAction = { Icon(Icons.Rounded.Style, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.onBackground) },
                                        items = ThemePaletteStyle.entries.map { it.displayName() },
                                        selectedIndex = ThemePaletteStyle.entries.indexOf(settings.paletteStyle).coerceAtLeast(0),
                                        onSelectedIndexChange = { AppSettingsRepository.setPaletteStyle(ThemePaletteStyle.entries[it]) },
                                    )
                                    val specs = ThemeColorSpec.entries
                                    OverlayDropdownPreference(
                                        title = "色彩规范",
                                        summary = settings.colorSpec.displayName(),
                                        startAction = { Icon(Icons.Rounded.DesignServices, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.onBackground) },
                                        items = specs.map { it.displayName() },
                                        selectedIndex = specs.indexOf(settings.colorSpec).coerceAtLeast(0),
                                        onSelectedIndexChange = { AppSettingsRepository.setColorSpec(specs[it]) },
                                    )
                                }
                            }
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
                            startAction = { Icon(Icons.Rounded.BlurOn, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.onBackground) },
                            checked = settings.enableBlur,
                            onCheckedChange = AppSettingsRepository::setEnableBlur,
                        )
                    }
                    SwitchPreference(
                        title = "悬浮底栏",
                        summary = "使用 KernelSU 风格的悬浮底栏",
                        startAction = { Icon(Icons.Rounded.CallToAction, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.onBackground) },
                        checked = settings.floatingBottomBar,
                        onCheckedChange = AppSettingsRepository::setFloatingBottomBar,
                    )
                    AnimatedVisibility(
                        visible = settings.floatingBottomBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                    ) {
                        SwitchPreference(
                            title = "液态玻璃",
                            summary = "启用悬浮底栏的液态玻璃效果",
                            startAction = { Icon(Icons.Rounded.WaterDrop, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.onBackground) },
                            checked = settings.floatingBottomBarBlur,
                            onCheckedChange = AppSettingsRepository::setFloatingBottomBarBlur,
                        )
                    }
                    SwitchPreference(
                        title = "导航栏角标",
                        summary = "在导航栏显示激活状态提示",
                        startAction = { Icon(Icons.Rounded.Notifications, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.onBackground) },
                        checked = settings.navigationBadge,
                        onCheckedChange = AppSettingsRepository::setNavigationBadge,
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
                            startAction = { Icon(Icons.AutoMirrored.Rounded.MenuOpen, null, Modifier.padding(end = 6.dp), tint = MiuixTheme.colorScheme.onBackground) },
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
                            ArrowPreference(
                                title = "最大预测返回进度",
                                summary = "控制返回手势预览的最大页面位移",
                                startAction = {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.MenuOpen,
                                        null,
                                        Modifier.padding(end = 6.dp),
                                        tint = MiuixTheme.colorScheme.onBackground,
                                    )
                                },
                                endActions = {
                                    Text(
                                        text = "${(sliderValue * 100).roundToInt()}%",
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                },
                                onClick = {},
                                bottomAction = {
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
                                },
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(settings: AppSettings) {
    val primary = MiuixTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Box(Modifier.fillMaxWidth().height(270.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(width = 180.dp, height = 250.dp)
                    .border(2.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                    .padding(10.dp),
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("主题预览", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Box(Modifier.fillMaxWidth().height(52.dp).background(primary.copy(alpha = 0.24f), RoundedCornerShape(10.dp)))
                    Box(Modifier.weight(1f).fillMaxWidth().background(MiuixTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp)))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(4) { Box(Modifier.size(28.dp).background(if (it == 0) primary else MiuixTheme.colorScheme.surfaceContainerHighest, CircleShape)) }
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
