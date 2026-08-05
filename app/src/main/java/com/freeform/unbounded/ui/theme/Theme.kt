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
    // KernelSU resolves the platform Monet primary first and passes it to MIUIX.
    // Without this key color, MIUIX can fall back to its default green while the
    // rest of the palette is dynamic, producing a mixed-color home card.
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

    // MIUIX only applies keyColor in Monet modes; this mirrors KernelSU's controller setup.
    val controller = ThemeController(
        mode,
        keyColor = keyColor,
        isDark = dark,
        paletteStyle = settings.paletteStyle,
        colorSpec = colorSpec,
    )

    UpdateSystemBars(dark)
    MiuixTheme(controller = controller) {
        CompositionLocalProvider(
            LocalDarkTheme provides dark,
            LocalContentColor provides MiuixTheme.colorScheme.onBackground,
            content = content,
        )
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

private fun ThemeColorSpec.effectiveFor(style: ThemePaletteStyle): ThemeColorSpec {
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
