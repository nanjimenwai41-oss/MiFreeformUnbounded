package com.freeform.unbounded

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

internal enum class DarkMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
    AMOLED("纯黑"),
}

internal data class AppSettings(
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val monetEnabled: Boolean = true,
    val seedColor: Int = DEFAULT_SEED_COLOR,
    val paletteStyle: ThemePaletteStyle = ThemePaletteStyle.TonalSpot,
    val colorSpec: ThemeColorSpec = ThemeColorSpec.Spec2025,
    val enableBlur: Boolean = true,
    val floatingBottomBar: Boolean = true,
    val floatingBottomBarBlur: Boolean = true,
    val navigationBadge: Boolean = true,
    val predictiveBack: Boolean = false,
    val fineAdjustmentEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_SEED_COLOR: Int = 0
    }
}

internal object AppSettingsRepository {
    private const val FILE_NAME = "appearance_settings"
    private const val LEGACY_DEFAULT_SEED_COLOR = 0xFF2F7D4A.toInt()
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    private var preferences: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (preferences != null) return
        preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        preferences?.edit()?.remove("fine_adjustment_color")?.apply()
        _settings.value = read()
    }

    fun setDarkMode(value: DarkMode) = update { it.copy(darkMode = value) }
    fun setMonetEnabled(value: Boolean) = update { it.copy(monetEnabled = value) }
    fun setSeedColor(value: Int) = update { it.copy(seedColor = value) }
    fun setPaletteStyle(value: ThemePaletteStyle) = update { it.copy(paletteStyle = value) }
    fun setColorSpec(value: ThemeColorSpec) = update { it.copy(colorSpec = value) }
    fun setEnableBlur(value: Boolean) = update { it.copy(enableBlur = value) }
    fun setFloatingBottomBar(value: Boolean) = update { it.copy(floatingBottomBar = value) }
    fun setFloatingBottomBarBlur(value: Boolean) = update { it.copy(floatingBottomBarBlur = value) }
    fun setNavigationBadge(value: Boolean) = update { it.copy(navigationBadge = value) }
    fun setPredictiveBack(value: Boolean) = update { it.copy(predictiveBack = value) }
    fun setFineAdjustmentEnabled(value: Boolean) = update { it.copy(fineAdjustmentEnabled = value) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val value = transform(_settings.value)
        _settings.value = value
        preferences?.edit()?.apply {
            putString("dark_mode", value.darkMode.name)
            putBoolean("monet", value.monetEnabled)
            putInt("seed_color", value.seedColor)
            putString("palette_style", value.paletteStyle.name)
            putString("color_spec", value.colorSpec.name)
            putBoolean("enable_blur", value.enableBlur)
            putBoolean("floating_bottom_bar", value.floatingBottomBar)
            putBoolean("floating_bottom_bar_blur", value.floatingBottomBarBlur)
            putBoolean("navigation_badge", value.navigationBadge)
            putBoolean("enable_predictive_back", value.predictiveBack)
            putBoolean("fine_adjustment_enabled", value.fineAdjustmentEnabled)
            remove("fine_adjustment_color")
            remove("predictive_back")
            remove("ui_mode")
        }?.apply()
    }

    private fun read(): AppSettings {
        val prefs = preferences ?: return AppSettings()
        val legacyBlur = prefs.getBoolean("bottom_bar_blur", true)
        return AppSettings(
            darkMode = enumValue(prefs.getString("dark_mode", null), DarkMode.SYSTEM),
            monetEnabled = prefs.getBoolean("monet", true),
            seedColor = readSeedColor(prefs),
            paletteStyle = enumValue(
                prefs.getString("palette_style", null),
                ThemePaletteStyle.TonalSpot,
            ),
            colorSpec = readColorSpec(prefs.getString("color_spec", null)),
            enableBlur = prefs.getBoolean("enable_blur", legacyBlur),
            floatingBottomBar = prefs.getBoolean("floating_bottom_bar", true),
            floatingBottomBarBlur = prefs.getBoolean("floating_bottom_bar_blur", legacyBlur),
            navigationBadge = prefs.getBoolean("navigation_badge", true),
            predictiveBack = prefs.getBoolean(
                "enable_predictive_back",
                prefs.getBoolean("predictive_back", false),
            ),
            fineAdjustmentEnabled = prefs.getBoolean("fine_adjustment_enabled", true),
        )
    }

    private fun readColorSpec(value: String?): ThemeColorSpec = when (value) {
        ThemeColorSpec.Spec2021.name, "SPEC_2021" -> ThemeColorSpec.Spec2021
        else -> ThemeColorSpec.Spec2025
    }

    private fun readSeedColor(prefs: android.content.SharedPreferences): Int {
        val saved = prefs.getInt("seed_color", AppSettings.DEFAULT_SEED_COLOR)
        return if (saved == LEGACY_DEFAULT_SEED_COLOR) AppSettings.DEFAULT_SEED_COLOR else saved
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
