// Adapted from KernelSU manager/ui/util/BlurExt.kt (GPL-3.0).

package com.freeform.unbounded.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 按设置及平台能力创建背景采样层。 */
@Composable
internal fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRuntimeShaderSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** MIUIX handles backdrop coordinates; native Gaussian layers stay at display resolution. */
@Composable
internal fun BlurredBar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    blurActive: Boolean = true,
    bottom: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        if (blurActive && backdrop != null) {
            // Four real blur radii, rather than fading one fixed blur over sharp content.
            // Mask each background plane separately so titles/actions are never blurred.
            for (level in 0..3) {
                val radiusFraction = floatArrayOf(.12f, .35f, .7f, 1f)[level]
                val fadeStart = (3 - level) * .22f
                val fadeEnd = minOf(1f, fadeStart + .34f)
                Box(
                    Modifier.matchParentSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val mask = if (bottom) {
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    (1f - fadeEnd) to Color.Transparent,
                                    (1f - fadeStart) to Color.White,
                                    1f to Color.White,
                                )
                            } else {
                                Brush.verticalGradient(
                                    0f to Color.White,
                                    fadeStart to Color.White,
                                    fadeEnd to Color.Transparent,
                                    1f to Color.Transparent,
                                )
                            }
                            drawRect(mask, blendMode = BlendMode.DstIn)
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RectangleShape },
                            effects = {
                                val radius = BlurDefaults.BlurRadius * radiusFraction * density
                                padding = maxOf(padding, radius * 3f)
                                downscaleFactor = 1
                                renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                            },
                        ),
                )
            }
        }
        content()
    }
}
