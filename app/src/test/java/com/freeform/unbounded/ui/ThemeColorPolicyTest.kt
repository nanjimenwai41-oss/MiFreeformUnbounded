package com.freeform.unbounded.ui

import androidx.compose.ui.graphics.Color
import com.freeform.unbounded.AppSettings
import com.freeform.unbounded.RestartScopes
import com.freeform.unbounded.ui.theme.resolveThemeKeyColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeColorPolicyTest {
    @Test
    fun defaultSeedUsesPlatformMonet() {
        assertNull(resolveThemeKeyColor(AppSettings.DEFAULT_SEED_COLOR))
    }

    @Test
    fun customSeedIsPassedToMiuix() {
        val seed = 0xff336699.toInt()
        assertEquals(Color(seed), resolveThemeKeyColor(seed))
    }

    @Test
    fun restartScopesContainEveryWallpaperOnce() {
        val scopes = RestartScopes.allStatic
        assertEquals(9, scopes.size)
        assertEquals(9, scopes.map { it.id }.distinct().size)
        assertEquals(RestartScopes.wallpapers.toSet(), scopes.drop(3).toSet())
    }

    @Test
    fun currentScopePresetIncludesOnlyCurrentWallpaperAndCoreScopes() {
        assertEquals(
            setOf(RestartScopes.SYSTEM_UI, RestartScopes.AOD, RestartScopes.MI_WALLPAPER, RestartScopes.SNOW_MOUNTAIN),
            RestartScopes.currentScopes(RestartScopes.SNOW_MOUNTAIN),
        )
        assertEquals(
            setOf(RestartScopes.SYSTEM_UI, RestartScopes.AOD, RestartScopes.MI_WALLPAPER),
            RestartScopes.currentScopes(null),
        )
    }

    @Test
    fun wallpaperPresetDoesNotAccidentallyIncludeSystemUi() {
        assertEquals(
            setOf(RestartScopes.MI_WALLPAPER, RestartScopes.EARTH),
            RestartScopes.currentWallpapers(RestartScopes.EARTH),
        )
        assertEquals(setOf(RestartScopes.MI_WALLPAPER), RestartScopes.currentWallpapers(null))
    }

    @Test
    fun marketingModelKeepsExactHardwareIdentifierWithoutRepeatingManufacturer() {
        assertEquals("REDMI K90 · 2510DRK44C", formatDeviceModel("REDMI K90", "Xiaomi", "2510DRK44C"))
        assertEquals("Xiaomi 15", formatDeviceModel("", "Xiaomi", "Xiaomi 15"))
        assertEquals("Xiaomi 2510DRK44C", formatDeviceModel("", "Xiaomi", "2510DRK44C"))
    }

    @Test
    fun hyperOsPropertiesTakePriorityOverLegacyMiuiCompatibilityVersion() {
        assertEquals("HyperOS 4.0 · OS4.0.5.0.XPKCNXM", formatOsVersion("OS4.0", "OS4.0.5.0.XPKCNXM", "V816"))
        assertEquals("HyperOS 4.0.5.0.XPKCNXM", formatOsVersion("", "OS4.0.5.0.XPKCNXM", "V816"))
        assertEquals("V14 · V14.0.1", formatOsVersion("", "V14.0.1", "V14"))
    }
}
