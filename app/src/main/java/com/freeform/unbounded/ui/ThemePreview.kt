// 微缩设备预览布局参考 KernelSU ColorPaletteScreenMiuix（GPL-3.0）。
package com.freeform.unbounded.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeform.unbounded.AppSettings
import com.freeform.unbounded.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ThemePreviewCard(settings: AppSettings) {
    val configuration = LocalConfiguration.current
    val ratio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.coerceAtLeast(1)
    val useRail = configuration.screenWidthDp / settings.pageScale >= 600f
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val backdrop = rememberBlurBackdrop(settings.enableBlur && settings.floatingBottomBarBlur)
    // 直接使用应用当前色板，避免预览再次生成 Monet 色板造成颜色偏差。
    val accent = if (settings.monetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        colors.primary.copy(alpha = if (isInDarkTheme()) 0.36f else 0.16f).compositeOver(colors.surfaceContainerHigh)
    } else if (isInDarkTheme()) Color(0xFF1A3825) else Color(0xFFDFFAE4)
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth(if (ratio > 1f) 0.68f else 0.4f).aspectRatio(ratio)
                .clip(shape).background(colors.surface).border(1.dp, colors.outline, shape),
        ) {
            Row(Modifier.fillMaxSize()) {
                if (useRail) {
                    Column(
                        Modifier.width(28.dp).fillMaxSize().background(colors.surfaceContainer).padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { PreviewNavIcons(settings.navigationBadge) }
                }
                Column(
                    Modifier.weight(1f).fillMaxSize()
                        .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                        .padding(bottom = if (useRail) 6.dp else 34.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().height(if (useRail) 30.dp else 46.dp).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Freeform Unbounded", fontSize = if (useRail) 9.sp else 10.sp, color = colors.onSurface, modifier = Modifier.weight(1f))
                        Icon(androidx.compose.ui.res.painterResource(com.freeform.unbounded.R.drawable.ic_restart), null, Modifier.size(11.dp), tint = colors.primary)
                    }
                    Box(
                        Modifier.padding(horizontal = 7.dp).fillMaxWidth().height(if (useRail) 30.dp else 45.dp)
                            .clip(RoundedCornerShape(7.dp)).background(accent),
                    ) {
                        Text("已激活", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface, modifier = Modifier.padding(8.dp))
                        Icon(
                            Icons.Rounded.CheckCircleOutline, null,
                            Modifier.align(Alignment.BottomEnd).size(35.dp).offset(8.dp, 10.dp),
                            tint = colors.primary,
                        )
                    }
                    BoxWithConstraints(Modifier.weight(1f).padding(horizontal = 7.dp, vertical = 6.dp)) {
                        val availableHeight = maxHeight
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(
                                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(6.dp))
                                    .background(colors.surfaceContainerHigh).padding(7.dp),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    repeat(if (availableHeight >= 55.dp) 3 else 1) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(colors.primary))
                                            Box(Modifier.height(3.dp).fillMaxWidth(0.6f).background(colors.onSurfaceVariantSummary.copy(alpha = 0.3f)))
                                        }
                                    }
                                }
                            }
                            if (availableHeight >= 95.dp) {
                                Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(6.dp)).background(colors.surfaceContainerHigh))
                            }
                        }
                    }
                }
            }
            if (!useRail) {
                val floating = settings.floatingBottomBar
                val navShape = RoundedCornerShape(if (floating) 18.dp else 0.dp)
                val glass = floating && backdrop != null
                Row(
                    Modifier.align(Alignment.BottomCenter)
                        .padding(horizontal = if (floating) 12.dp else 0.dp, vertical = if (floating) 8.dp else 0.dp)
                        .fillMaxWidth(if (floating) 0.86f else 1f).height(if (floating) 28.dp else 34.dp)
                        .clip(navShape)
                        .then(if (glass) Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = navShape,
                            blurRadius = 8f,
                            colors = BlurColors(blendColors = listOf(BlendColorEntry(colors.surfaceContainer.copy(alpha = 0.65f)))),
                        ) else Modifier.background(colors.surfaceContainer))
                        .border(if (floating) 0.5.dp else 0.dp, colors.outline.copy(alpha = 0.4f), navShape),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) { PreviewNavIcons(settings.navigationBadge) }
            }
        }
    }
}

@Composable
private fun PreviewNavIcons(showBadge: Boolean) {
    val colors = MiuixTheme.colorScheme
    listOf(Icons.Rounded.Home, Icons.Rounded.Tune, Icons.Rounded.Settings).forEachIndexed { index, icon ->
        Box {
            Icon(icon, null, Modifier.size(13.dp), tint = if (index == 0) colors.primary else colors.onSurfaceVariantSummary)
            if (index == 0 && showBadge) {
                Spacer(Modifier.align(Alignment.TopEnd).size(3.dp).background(colors.primary, RoundedCornerShape(2.dp)))
            }
        }
    }
}
