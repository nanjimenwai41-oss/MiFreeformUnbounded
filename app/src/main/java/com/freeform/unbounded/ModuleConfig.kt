package com.freeform.unbounded

import android.content.SharedPreferences

internal data class AppConfig(
    val securityMarginPx: Int = ModuleConfigKeys.DEFAULT_MARGIN,
    val freeformBoundaryEnabled: Boolean = false,
    val aodGlassEnabled: Boolean = false,
    val superWallpaperDepthEnabled: Boolean = false,
)

internal object ModuleConfigKeys {
    const val GROUP = "freeform_unbounded"
    const val LOCAL_FILE = "freeform_unbounded_local"
    const val SECURITY_MARGIN = "security_boundary_margin_px"
    const val FREEFORM_BOUNDARY_ENABLED = "freeform_boundary_enabled"
    const val AOD_GLASS_ENABLED = "aod_glass_enabled"
    const val SUPER_WALLPAPER_DEPTH_ENABLED = "super_wallpaper_depth_enabled"

    const val DEFAULT_MARGIN = 196
    const val LEGACY_DEFAULT_MARGIN = 48
    const val MIN_MARGIN = 8
    const val MAX_MARGIN = 320

    val LEGACY_KEYS = arrayOf(
        "security_boundary_enabled",
        "app_list_enabled",
        "app_list_mode",
        "app_list_packages",
    )
}

internal fun AppConfig.writeTo(editor: SharedPreferences.Editor): SharedPreferences.Editor {
    editor.putInt(ModuleConfigKeys.SECURITY_MARGIN, securityMarginPx)
    editor.putBoolean(ModuleConfigKeys.FREEFORM_BOUNDARY_ENABLED, freeformBoundaryEnabled)
    editor.putBoolean(ModuleConfigKeys.AOD_GLASS_ENABLED, aodGlassEnabled)
    editor.putBoolean(
        ModuleConfigKeys.SUPER_WALLPAPER_DEPTH_ENABLED,
        superWallpaperDepthEnabled,
    )
    ModuleConfigKeys.LEGACY_KEYS.forEach(editor::remove)
    return editor
}

internal fun SharedPreferences.readConfig(): AppConfig = AppConfig(
    securityMarginPx = normalizeSecurityMargin(getInt(
        ModuleConfigKeys.SECURITY_MARGIN,
        ModuleConfigKeys.DEFAULT_MARGIN,
    )),
    freeformBoundaryEnabled = getBoolean(ModuleConfigKeys.FREEFORM_BOUNDARY_ENABLED, false),
    aodGlassEnabled = getBoolean(ModuleConfigKeys.AOD_GLASS_ENABLED, false),
    superWallpaperDepthEnabled = getBoolean(
        ModuleConfigKeys.SUPER_WALLPAPER_DEPTH_ENABLED,
        false,
    ),
)

internal fun normalizeSecurityMargin(value: Int): Int =
    (if (value == ModuleConfigKeys.LEGACY_DEFAULT_MARGIN) ModuleConfigKeys.DEFAULT_MARGIN else value)
        .coerceIn(ModuleConfigKeys.MIN_MARGIN, ModuleConfigKeys.MAX_MARGIN)
