package com.freeform.unbounded

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassBypassPolicyTest {
    @Test
    fun enablesGlassForDynamicWallpaperInAllInOne() {
        assertTrue(GlassBypassPolicy.shouldOverride(5, true, config("video")))
        assertTrue(GlassBypassPolicy.shouldOverride(5, true, config("super_wallpaper")))
    }

    @Test
    fun preservesStockDecisionForStaticWallpaper() {
        assertFalse(GlassBypassPolicy.shouldOverride(5, true, config("image")))
        assertFalse(GlassBypassPolicy.shouldOverride(5, true, config("gallery")))
    }

    @Test
    fun onlyOverridesTheGlassEffectAndAllInOneTemplate() {
        assertFalse(GlassBypassPolicy.shouldOverride(4, true, config("video")))
        assertFalse(GlassBypassPolicy.shouldOverride(5, false, config("video")))
        assertFalse(GlassBypassPolicy.shouldOverride(5, true, config("video", template = "classic")))
        assertFalse(GlassBypassPolicy.shouldOverride(5, true, null))
    }

    @Test
    fun preservesGlassEffectWhenStockDowngradesDynamicWallpaper() {
        assertTrue(GlassBypassPolicy.shouldPreserveGlassEffect(5, config("video")))
        assertFalse(GlassBypassPolicy.shouldPreserveGlassEffect(4, config("video")))
        assertFalse(GlassBypassPolicy.shouldPreserveGlassEffect(5, config("image")))
    }

    @Test
    fun recognizesDynamicWallpaperFilterArguments() {
        assertTrue(GlassBypassPolicy.shouldAllowGlassWallpaperFilter(arrayOf("video")))
        assertTrue(GlassBypassPolicy.shouldAllowGlassWallpaperFilter(arrayOf("super_wallpaper")))
        assertFalse(GlassBypassPolicy.shouldAllowGlassWallpaperFilter(arrayOf("image")))
    }

    @Test
    fun restoresSystemUiGlassFromGlassMarkersDuringRebind() {
        assertTrue(
            GlassBypassPolicy.shouldRestoreSystemUiGlass(
                ControllerFixture(true),
                ClockBeanFixture(),
            ),
        )
        assertFalse(
            GlassBypassPolicy.shouldRestoreSystemUiGlass(
            null,
            ClockBeanFixture(),
            ),
        )
        assertFalse(
            GlassBypassPolicy.shouldRestoreSystemUiGlass(
                ControllerFixture(true),
                ClockBeanFixture(clockEffect = 2),
            ),
        )
    }

    @Test
    fun restoresPersistedGlassBeanWhenSystemUiDepthStateIsFalse() {
        assertTrue(
            GlassBypassPolicy.shouldRestorePersistedSystemUiGlass(
                ClockBeanFixture(),
            ),
        )
        assertFalse(
            GlassBypassPolicy.shouldRestorePersistedSystemUiGlass(
                ClockBeanFixture(style = 4),
            ),
        )
    }

    @Test
    fun recognizesHyperOs4GlassBeanMarkers() {
        assertTrue(
            GlassBypassPolicy.shouldRestorePersistedSystemUiGlass(
                ClockBeanFixture(fontStyle = 22, hollowStyle = 0, glassTransparency = 0.15f),
            ),
        )
    }

    @Test
    fun recognizesFilledHyperOs4GlassBeanMarkers() {
        assertTrue(
            GlassBypassPolicy.shouldRestorePersistedSystemUiGlass(
                ClockBeanFixture(fontStyle = 22, hollowStyle = 0, glassTransparency = 0.15f),
            ),
        )
    }

    @Test
    fun rejectsPresetOnlyStyleFamiliesWithoutRuntimeGlassEvidence() {
        listOf(1 to 21, 2 to 21, 3 to 21, 4 to 21, 6 to 21, 7 to 22, 8 to 23).forEach { (style, fontStyle) ->
            assertFalse(
                "style=$style fontStyle=$fontStyle",
                GlassBypassPolicy.shouldRestorePersistedSystemUiGlass(
                    ClockBeanFixture(style = style, fontStyle = fontStyle, glassTransparency = 0.15f),
                ),
            )
        }
    }

    @Test
    fun rejectsUnknownOrMalformedGlassMarkers() {
        listOf(
            ClockBeanFixture(style = 5, fontStyle = 24, glassTransparency = 0.15f),
            ClockBeanFixture(style = 7, fontStyle = 24, glassTransparency = 0.15f),
            ClockBeanFixture(style = 9, fontStyle = 21, glassTransparency = 0.15f),
            ClockBeanFixture(style = 1, fontStyle = 0, glassTransparency = 0.15f),
            ClockBeanFixture(style = 1, fontStyle = 22, glassTransparency = 0.21f),
            ClockBeanFixture(style = 1, fontStyle = 22, glassTransparency = -0.01f),
        ).forEach { bean ->
            assertFalse(GlassBypassPolicy.shouldRestorePersistedSystemUiGlass(bean))
        }
    }

    private fun config(resourceType: String, template: String = "all_in_one"): Any =
        CommonConfigFixture(template, WallpaperFixture(resourceType))

    private data class CommonConfigFixture(
        private val template: String,
        private val wallpaper: WallpaperFixture,
    ) {
        fun getLockscreenInfo(): LockScreenFixture = LockScreenFixture(template, wallpaper)
    }

    private data class LockScreenFixture(
        private val template: String,
        private val wallpaper: WallpaperFixture,
    ) {
        fun getClockInfo(): ClockFixture = ClockFixture(template)
        fun getWallpaperInfo(): WallpaperFixture = wallpaper
    }

    private data class ClockFixture(private val template: String) {
        fun getTemplateId(): String = template
    }

    private data class WallpaperFixture(private val resourceType: String) {
        fun getResourceType(): String = resourceType
    }

    private data class ControllerFixture(private val depth: Boolean) {
        fun isWallpaperSupportDepth(): Boolean = depth
    }

    private data class ClockBeanFixture(
        private val template: String = "all_in_one",
        private val clockEffect: Int = 1,
        private val style: Int = 5,
        private val fontStyle: Int = 22,
        private val hollowStyle: Int = 0,
        private val glassTransparency: Float = 0.15f,
    ) {
        fun getTemplateId(): String = template
        fun getClockEffect(): Int = clockEffect
        fun getStyle(): Int = style
        fun getFontStyle(): Int = fontStyle
        fun getHollowStyle(): Int = hollowStyle
        fun getGlassTransparency(): Float = glassTransparency
    }
}
