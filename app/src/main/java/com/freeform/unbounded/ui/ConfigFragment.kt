package com.freeform.unbounded.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.freeform.unbounded.AppSettingsRepository
import com.freeform.unbounded.ConfigRepository
import com.freeform.unbounded.ModuleConfigKeys
import com.freeform.unbounded.ui.theme.isInDarkTheme
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ConfigScreen(
    onOpenFreeformBoundary: () -> Unit,
    onOpenTheme: () -> Unit,
    enableBlur: Boolean,
    floatingBottomBar: Boolean,
) {
    val config by ConfigRepository.config.collectAsState()
    val context = LocalContext.current
    var showGlassWarning by remember { mutableStateOf(false) }
    var showResetWarning by remember { mutableStateOf(false) }
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    val navigationBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPadding = if (floatingBottomBar) {
        // The floating bar is 64.dp high, sits 12.dp above the navigation inset,
        // and is drawn over the pager rather than inside Scaffold.
        104.dp + navigationBottomInset
    } else {
        32.dp + navigationBottomInset
    }
    Scaffold(
        topBar = {
            BlurredBar(blurBackdrop) {
                TopAppBar(
                    color = barColor,
                    title = "设置",
                    largeTitle = "设置",
                )
            }
        },
    ) { padding ->
        Box(
            modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier,
        ) {
            LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = contentBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionLabel("模块功能") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "自由窗口边界保护",
                        summary = "总开关、最小可见距离和精细调节",
                        startAction = {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        onClick = onOpenFreeformBoundary,
                    )
                    SwitchPreference(
                        title = "强制使用玻璃时钟",
                        summary = "在不支持玻璃时钟的场景下（例如动态壁纸和超级壁纸）强制使用锁屏玻璃时钟；修改后请重启系统界面和息屏与锁屏编辑，重启后生效",
                        startAction = {
                            Icon(
                                Icons.Rounded.Wallpaper,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        checked = config.aodGlassEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showGlassWarning = true
                            } else {
                                ConfigRepository.setAodGlassEnabled(false)
                            }
                        },
                    )
                    SwitchPreference(
                        title = "超级壁纸景深",
                        summary = "将动态视频壁纸的景深算法用于超级壁纸；修改后请重启系统界面，重启后生效",
                        startAction = {
                            Icon(
                                Icons.Rounded.Layers,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 6.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        },
                        checked = config.superWallpaperDepthEnabled,
                        onCheckedChange = ConfigRepository::setSuperWallpaperDepthEnabled,
                    )
                }
            }
            item { SectionLabel("外观") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "主题与底栏",
                        summary = "深色模式、Monet、色板、主题色和液态玻璃底栏",
                        onClick = onOpenTheme,
                    )
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "恢复默认配置",
                        summary = "边缘距离 196px · 模块功能均关闭",
                        onClick = {
                            showResetWarning = true
                        },
                    )
                }
            }
        }
        }
        GlassClockWarningDialog(
            show = showGlassWarning,
            onDismissRequest = { showGlassWarning = false },
            onConfirm = {
                ConfigRepository.setAodGlassEnabled(true)
                showGlassWarning = false
            },
        )
        ResetConfigWarningDialog(
            show = showResetWarning,
            onDismissRequest = { showResetWarning = false },
            onConfirm = {
                ConfigRepository.reset()
                showResetWarning = false
                Toast.makeText(context, "已恢复默认配置", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
internal fun FreeformBoundaryScreen(
    enableBlur: Boolean,
    moduleReady: Boolean,
    onBack: () -> Unit,
) {
    val config by ConfigRepository.config.collectAsState()
    val settings by AppSettingsRepository.settings.collectAsState()
    val context = LocalContext.current
    var showMarginDialog by remember { mutableStateOf(false) }
    var sliderValue by remember(config.securityMarginPx) {
        mutableFloatStateOf(config.securityMarginPx.toFloat())
    }
    var rawSliderValue by remember { mutableFloatStateOf(sliderValue) }
    var fineAdjustmentGestureStarted by remember { mutableStateOf(false) }
    val controlsEnabled = moduleReady && config.freeformBoundaryEnabled
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    val adjustmentCardColor = if (isInDarkTheme()) Color(0xFF424242) else Color(0xFFE0E0E0)
    val adjustmentContentColor = if (isInDarkTheme()) Color(0xFFBDBDBD) else Color(0xFF616161)
    val inactiveToast = {
        Toast.makeText(
            context,
            "模块未激活",
            Toast.LENGTH_SHORT,
        ).show()
    }
    Scaffold(
        topBar = {
            BlurredBar(blurBackdrop) {
                TopAppBar(
                    color = barColor,
                    title = "自由窗口边界保护",
                    largeTitle = "自由窗口边界保护",
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = 16.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            title = "自由窗口边界保护",
                            summary = "总开关；关闭后下方的边缘距离和精细调节不可用",
                            startAction = {
                                Icon(
                                    Icons.Rounded.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp),
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            },
                            checked = config.freeformBoundaryEnabled,
                            onCheckedChange = { enabled ->
                                ConfigRepository.setFreeformBoundaryEnabled(enabled)
                                if (!enabled) showMarginDialog = false
                            },
                        )
                    }
                }
                item {
                    Text(
                        "开启总开关后，下面的边缘距离设置才会生效；修改后请重启系统界面。",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
                item { SectionLabel("边缘距离", enabled = controlsEnabled) }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (controlsEnabled) 1f else 0.62f),
                        colors = if (controlsEnabled) {
                            CardDefaults.defaultColors()
                        } else {
                            CardDefaults.defaultColors(
                                color = adjustmentCardColor,
                                contentColor = adjustmentContentColor,
                            )
                        },
                        insideMargin = PaddingValues(18.dp),
                    ) {
                        Text("窗口控制", style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
                        Text(
                            "调整自由窗口拖到屏幕边缘时保留的最小可见距离。数值写入系统界面与模块服务共享配置，重启系统界面后应用。",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (controlsEnabled) 1f else 0.62f),
                        colors = if (controlsEnabled) {
                            CardDefaults.defaultColors()
                        } else {
                            CardDefaults.defaultColors(
                                color = adjustmentCardColor,
                                contentColor = adjustmentContentColor,
                            )
                        },
                        onClick = { if (!controlsEnabled && !moduleReady) inactiveToast() },
                        showIndication = controlsEnabled,
                    ) {
                        ArrowPreference(
                            title = "最小可见距离",
                            summary = "当前：${sliderValue.roundToInt()}px · 默认：196px · 范围：${ModuleConfigKeys.MIN_MARGIN}-${ModuleConfigKeys.MAX_MARGIN}px",
                            holdDownState = showMarginDialog,
                            onClick = {
                                if (controlsEnabled) {
                                    showMarginDialog = true
                                } else if (!moduleReady) {
                                    inactiveToast()
                                }
                            },
                            bottomAction = {
                                Slider(
                                    value = sliderValue,
                                    enabled = controlsEnabled,
                                    onValueChange = { rawValue ->
                                        if (!controlsEnabled) {
                                            if (!moduleReady) inactiveToast()
                                            return@Slider
                                        }
                                        if (!settings.fineAdjustmentEnabled) {
                                            sliderValue = rawValue
                                            rawSliderValue = rawValue
                                            fineAdjustmentGestureStarted = false
                                        } else {
                                            if (fineAdjustmentGestureStarted) {
                                                sliderValue = applyFineAdjustment(
                                                    currentValue = sliderValue,
                                                    rawValue = rawValue,
                                                    previousRawValue = rawSliderValue,
                                                )
                                            } else {
                                                fineAdjustmentGestureStarted = true
                                            }
                                            rawSliderValue = rawValue
                                        }
                                    },
                                    onValueChangeFinished = {
                                        if (!controlsEnabled) return@Slider
                                        fineAdjustmentGestureStarted = false
                                        rawSliderValue = sliderValue
                                        ConfigRepository.setSecurityMargin(sliderValue.roundToInt())
                                    },
                                    valueRange = ModuleConfigKeys.MIN_MARGIN.toFloat()..ModuleConfigKeys.MAX_MARGIN.toFloat(),
                                )
                            },
                        )
                        OverlayDropdownPreference(
                            title = "精细调节",
                            summary = FINE_ADJUSTMENT_OPTIONS[if (settings.fineAdjustmentEnabled) 0 else 1],
                            startAction = {
                                Icon(
                                    Icons.Rounded.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp),
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            },
                            items = FINE_ADJUSTMENT_OPTIONS,
                            selectedIndex = if (settings.fineAdjustmentEnabled) 0 else 1,
                            enabled = controlsEnabled,
                            onSelectedIndexChange = { index ->
                                if (controlsEnabled) {
                                    AppSettingsRepository.setFineAdjustmentEnabled(index == 0)
                                } else if (!moduleReady) {
                                    inactiveToast()
                                }
                            },
                        )
                    }
                }
            }
        }
        SecurityMarginDialog(
            show = showMarginDialog,
            currentValue = config.securityMarginPx,
            onDismissRequest = { showMarginDialog = false },
            onConfirm = { value ->
                ConfigRepository.setSecurityMargin(value)
                showMarginDialog = false
            },
        )
    }
}

@Composable
private fun SecurityMarginDialog(
    show: Boolean,
    currentValue: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    if (!show) return

    val initialText = currentValue.toString()
    var text by remember(currentValue) {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(0, initialText.length),
            ),
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            withFrameNanos { }
            keyboardController?.show()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                cornerRadius = 36.dp,
                insideMargin = PaddingValues(24.dp),
            ) {
                Text(
                    text = "最小可见距离",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "当前：${currentValue}px",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                Text(
                    text = "默认：${ModuleConfigKeys.DEFAULT_MARGIN}px · 范围：${ModuleConfigKeys.MIN_MARGIN}-${ModuleConfigKeys.MAX_MARGIN}px",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 16.dp),
                )
                TextField(
                    value = text,
                    maxLines = 1,
                    colors = TextFieldDefaults.textFieldColors(
                        // Keep this numeric editor neutral even when Monet generates a colored
                        // secondary container for the rest of the settings surface.
                        backgroundColor = Color.White,
                        labelColor = Color(0xFF424242),
                        borderColor = Color(0xFF757575),
                    ),
                    textStyle = MiuixTheme.textStyles.main.copy(color = Color.Black),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        Text(
                            text = "px",
                            color = Color(0xFF616161),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    },
                    onValueChange = { value ->
                        if (value.text.isEmpty() || value.text.all(Char::isDigit)) {
                            text = value
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .padding(bottom = 16.dp),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        text = "取消",
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = "确定",
                        onClick = {
                            val value = text.text.toIntOrNull()
                                ?.coerceIn(ModuleConfigKeys.MIN_MARGIN, ModuleConfigKeys.MAX_MARGIN)
                                ?: currentValue
                            onConfirm(value)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassClockWarningDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val dialogState = remember { MutableTransitionState(false) }
    LaunchedEffect(show) {
        dialogState.targetState = show
    }
    if (!dialogState.currentState && !dialogState.targetState) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = dialogState,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + scaleIn(
                    initialScale = 0.96f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(animationSpec = tween(100)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(180),
                ) + scaleOut(
                    targetScale = 0.98f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    animationSpec = tween(180),
                ) + fadeOut(animationSpec = tween(120)),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    cornerRadius = 36.dp,
                    insideMargin = PaddingValues(24.dp),
                ) {
                    Text(
                        text = "强制使用玻璃时钟",
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "开启该功能之后功耗将升高，请考虑是否开启。由于技术限制，目前会使得在息屏与锁屏编辑当中的数字材质强制为玻璃，其他选项无效，若需重新启用其他选项需要关闭模块开关，是否确认？",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 20.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            text = "取消",
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = "确认开启",
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResetConfigWarningDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val dialogState = remember { MutableTransitionState(false) }
    LaunchedEffect(show) {
        dialogState.targetState = show
    }
    if (!dialogState.currentState && !dialogState.targetState) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = dialogState,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + scaleIn(
                    initialScale = 0.96f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) + fadeIn(animationSpec = tween(100)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(180),
                ) + scaleOut(
                    targetScale = 0.98f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    animationSpec = tween(180),
                ) + fadeOut(animationSpec = tween(120)),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp),
                    cornerRadius = 36.dp,
                    insideMargin = PaddingValues(24.dp),
                ) {
                    Text(
                        text = "恢复默认配置",
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "将边缘距离恢复为 196px，并关闭自由窗口边界保护、强制玻璃时钟和超级壁纸景深。此操作会同步写入模块配置，是否继续？",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 20.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            text = "取消",
                            onClick = onDismissRequest,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = "恢复默认",
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    enabled: Boolean = true,
) {
    Text(
        text,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.62f)
            .padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
    )
}

private const val FINE_ADJUSTMENT_SENSITIVITY = 0.25f
private val FINE_ADJUSTMENT_OPTIONS = listOf("开启", "关闭")

internal fun applyFineAdjustment(
    currentValue: Float,
    rawValue: Float,
    previousRawValue: Float,
): Float = (currentValue + (rawValue - previousRawValue) * FINE_ADJUSTMENT_SENSITIVITY).coerceIn(
    ModuleConfigKeys.MIN_MARGIN.toFloat(),
    ModuleConfigKeys.MAX_MARGIN.toFloat(),
)
