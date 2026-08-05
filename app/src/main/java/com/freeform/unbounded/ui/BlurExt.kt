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
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * KernelSU-style blur pipeline: only create a backdrop when the setting and
 * the platform's RenderEffect implementation are both available.
 */
@Composable
internal fun rememberBlurBackdrop(enableBlur: Boolean): LayerBackdrop? {
    if (!enableBlur || !isRenderEffectSupported()) return null
    val surfaceColor = MiuixTheme.colorScheme.surface
    return rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
}

/** Applies the same translucent texture blur used by KernelSU to a system bar. */
@Composable
internal fun BlurredBar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    blurActive: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.then(if (blurActive && backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = 18f,
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
