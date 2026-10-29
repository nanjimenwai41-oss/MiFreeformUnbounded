// Adapted from KernelSU manager/ui/util/BlurExt.kt (GPL-3.0).

package com.freeform.unbounded.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 按设置及平台能力创建背景采样层。 */
@Composable
internal fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** 只渐变模糊背景，标题与操作按钮保持清晰。 */
@Composable
internal fun BlurredBar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    blurActive: Boolean = true,
    bottom: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.then(if (blurActive && backdrop != null) {
            Modifier.progressiveTextureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 18f,
                gradient = if (bottom) ProgressiveBlur.Bottom else ProgressiveBlur.Top,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(
                            color = MiuixTheme.colorScheme.surface.copy(alpha = 0.65f),
                        ),
                    ),
                ),
            )
        } else {
            Modifier
        }),
    ) {
        content()
    }
}
