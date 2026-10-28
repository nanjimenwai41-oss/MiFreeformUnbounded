package com.freeform.unbounded.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.freeform.unbounded.AppSettings
import com.freeform.unbounded.DarkMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

private val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
internal fun FreeformUnboundedTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.darkMode) {
        DarkMode.SYSTEM -> systemDark
        DarkMode.LIGHT -> false
        DarkMode.DARK, DarkMode.AMOLED -> true
    }
    val monetAvailable = settings.monetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val mode = resolveColorSchemeMode(settings.darkMode, monetAvailable)
    // 先解析系统 Monet 主色再交给 MIUIX，避免状态卡和动态主题各用一套颜色。
    val keyColor = when {
        settings.seedColor != AppSettings.DEFAULT_SEED_COLOR -> Color(settings.seedColor)
        monetAvailable -> if (dark) {
            dynamicDarkColorScheme(context).primary
        } else {
            dynamicLightColorScheme(context).primary
        }
        else -> null
    }
    val colorSpec = settings.colorSpec.effectiveFor(settings.paletteStyle)

    // MIUIX 只在 Monet 模式下应用种子色。
    val controller = ThemeController(
        mode,
        keyColor = keyColor,
        isDark = dark,
        paletteStyle = settings.paletteStyle,
        colorSpec = colorSpec,
    )

    UpdateSystemBars(dark)
    MiuixTheme(controller = controller) {
        val contentColor = MiuixTheme.colorScheme.onBackground
        val themedContent: @Composable () -> Unit = {
            CompositionLocalProvider(
                LocalDarkTheme provides dark,
                LocalContentColor provides contentColor,
                content = content,
            )
        }
        if (settings.darkMode == DarkMode.AMOLED) {
            val colors = MiuixTheme.colorScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceVariant = Color(0xFF0B0B0B),
                surfaceContainer = Color.Black,
                surfaceContainerHigh = Color(0xFF0A0A0A),
                surfaceContainerHighest = Color(0xFF151515),
                outline = Color(0xFF353535),
                dividerLine = Color(0xFF242424),
            )
            MiuixTheme(colors = colors, content = themedContent)
        } else {
            themedContent()
        }
    }
}

internal fun resolveColorSchemeMode(darkMode: DarkMode, monetEnabled: Boolean): ColorSchemeMode = when {
    monetEnabled && darkMode == DarkMode.SYSTEM -> ColorSchemeMode.MonetSystem
    monetEnabled && darkMode == DarkMode.LIGHT -> ColorSchemeMode.MonetLight
    monetEnabled -> ColorSchemeMode.MonetDark
    darkMode == DarkMode.SYSTEM -> ColorSchemeMode.System
    darkMode == DarkMode.LIGHT -> ColorSchemeMode.Light
    else -> ColorSchemeMode.Dark
}

internal fun ThemeColorSpec.effectiveFor(style: ThemePaletteStyle): ThemeColorSpec {
    val supportsSpec2025 = style == ThemePaletteStyle.TonalSpot ||
        style == ThemePaletteStyle.Neutral ||
        style == ThemePaletteStyle.Vibrant ||
        style == ThemePaletteStyle.Expressive
    return if (this == ThemeColorSpec.Spec2025 && !supportsSpec2025) {
        ThemeColorSpec.Spec2021
    } else {
        this
    }
}

@Composable
@ReadOnlyComposable
internal fun isInDarkTheme(): Boolean = LocalDarkTheme.current

@Composable
private fun UpdateSystemBars(dark: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(dark) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
