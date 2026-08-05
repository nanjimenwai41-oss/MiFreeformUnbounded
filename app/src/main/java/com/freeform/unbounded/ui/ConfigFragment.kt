package com.freeform.unbounded.ui

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ConfigScreen(
    onOpenTheme: () -> Unit,
    enableBlur: Boolean,
    moduleReady: Boolean,
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
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val barColor = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    val inactiveToast = {
        Toast.makeText(context, "模块未激活", Toast.LENGTH_SHORT).show()
    }
    val adjustmentCardColor = if (isInDarkTheme()) Color(0xFF424242) else Color(0xFFE0E0E0)
    val adjustmentContentColor = if (isInDarkTheme()) Color(0xFFBDBDBD) else Color(0xFF616161)
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
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionLabel("功能介绍") }
            item {
                Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(18.dp)) {
                    Text("窗口控制", style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
                    Text(
                        "模块对所有应用始终启用安全边界。拖动小窗时会根据当前屏幕和窗口尺寸，限制窗口内侧边缘与屏幕边框的最小距离；横竖屏、分辨率和窗口大小变化都会实时重新计算。",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item { SectionLabel("边缘距离") }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (moduleReady) 1f else 0.62f),
                    colors = if (moduleReady) {
                        CardDefaults.defaultColors()
                    } else {
                        CardDefaults.defaultColors(
                            color = adjustmentCardColor,
                            contentColor = adjustmentContentColor,
                        )
                    },
                    onClick = { if (!moduleReady) inactiveToast() },
                    showIndication = moduleReady,
                ) {
                    ArrowPreference(
                        title = "最小可见距离",
                        summary = "当前：${sliderValue.roundToInt()}px · 默认：196px · 范围：${ModuleConfigKeys.MIN_MARGIN}-${ModuleConfigKeys.MAX_MARGIN}px",
                        holdDownState = showMarginDialog,
                        onClick = { if (moduleReady) showMarginDialog = true else inactiveToast() },
                        bottomAction = {
                            Slider(
                                value = sliderValue,
                                enabled = moduleReady,
                                onValueChange = { rawValue ->
                                    if (!moduleReady) {
                                        inactiveToast()
                                        return@Slider
                                    }
                                    if (!settings.fineAdjustmentEnabled) {
                                        sliderValue = rawValue
                                        rawSliderValue = rawValue
                                        fineAdjustmentGestureStarted = false
                                    } else {
                                        // MIUIX derives values from cumulative physical drag distance.
                                        // Scale each raw delta so the thumb stays visible but moves more slowly.
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
                                    if (!moduleReady) return@Slider
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
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        },
                        items = FINE_ADJUSTMENT_OPTIONS,
                        selectedIndex = if (settings.fineAdjustmentEnabled) 0 else 1,
                        onSelectedIndexChange = { index ->
                            if (moduleReady) {
                                AppSettingsRepository.setFineAdjustmentEnabled(index == 0)
                            } else {
                                inactiveToast()
                            }
                        },
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        Text(
                            text = "px",
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
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
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
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
