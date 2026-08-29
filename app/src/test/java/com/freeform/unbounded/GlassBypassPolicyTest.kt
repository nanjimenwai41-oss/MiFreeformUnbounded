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
    fun restoresSystemUiGlassOnlyForDepthWallpaperGlassBean() {
        assertTrue(
            GlassBypassPolicy.shouldRestoreSystemUiGlass(
                ControllerFixture(true),
                ClockBeanFixture(),
            ),
        )
        assertFalse(
            GlassBypassPolicy.shouldRestoreSystemUiGlass(
                ControllerFixture(false),
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
        private val fontStyle: Int = 24,
        private val hollowStyle: Int = 1,
        private val glassTransparency: Float = 0.01f,
    ) {
        fun getTemplateId(): String = template
        fun getClockEffect(): Int = clockEffect
        fun getStyle(): Int = style
        fun getFontStyle(): Int = fontStyle
        fun getHollowStyle(): Int = hollowStyle
        fun getGlassTransparency(): Float = glassTransparency
    }
}
