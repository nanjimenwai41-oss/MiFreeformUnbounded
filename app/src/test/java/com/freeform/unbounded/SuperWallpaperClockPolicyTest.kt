package com.freeform.unbounded

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperWallpaperClockPolicyTest {
    @Test
    fun recognizesSuperAndDepthResourceTypesWithoutTreatingStaticAsDynamic() {
        assertTrue(SuperWallpaperClockPolicy.isSuperWallpaperResourceType("super_wallpaper"))
        assertTrue(SuperWallpaperClockPolicy.isSuperWallpaperResourceType("miui_super_wallpaper_v2"))
        assertTrue(SuperWallpaperClockPolicy.isDepthWallpaperResourceType("depth_video"))
        assertTrue(SuperWallpaperClockPolicy.isDepthWallpaperResourceType("super_wallpaper"))
        assertTrue(SuperWallpaperClockPolicy.isDynamicResourceType("live_photo"))
        assertFalse(SuperWallpaperClockPolicy.isDynamicResourceType("image"))
        assertFalse(SuperWallpaperClockPolicy.isDepthWallpaperResourceType("image"))
    }

    @Test
    fun identifiesNestedSuperWallpaperConfiguration() {
        val config = CommonConfig(LockscreenInfo(WallpaperInfo("super_wallpaper")))

        assertTrue(SuperWallpaperClockPolicy.isSuperWallpaperConfig(config))
        assertTrue(SuperWallpaperClockPolicy.isSuperWallpaperTarget(config))
        assertTrue(SuperWallpaperClockPolicy.isDepthWallpaperConfig(config))
        assertTrue(SuperWallpaperClockPolicy.isDepthWallpaperTarget(config))
    }

    @Test
    fun readsClockBeanValuesThroughAccessorOrFieldNames() {
        val bean = ClockBean(
            primaryColor = 0xff112233.toInt(),
            secondaryColor = 0xff445566.toInt(),
            timeWidth = 420f,
            timeHeight = 120f,
            clockTopRatio = 0.32f,
            clockDepthType = 2,
            enableDiffusion = true,
        )

        val values = SuperWallpaperClockPolicy.readClockValuesFrom(bean)

        assertNotNull(values)
        assertEquals(0xff112233.toInt(), values?.primaryColor)
        assertEquals(0xff445566.toInt(), values?.secondaryColor)
        assertEquals(420f, values?.timeWidth)
        assertEquals(120f, values?.timeHeight)
        assertEquals(2, values?.depthType)
        assertEquals(true, values?.diffusion)
    }

    @Test
    fun normalizesScaleAndInvalidFallbackAtTheBoundary() {
        assertEquals(0.25f, SuperWallpaperClockPolicy.normalizeScale(0.01f), 0f)
        assertEquals(4f, SuperWallpaperClockPolicy.normalizeScale(20f), 0f)
        assertEquals(1.5f, SuperWallpaperClockPolicy.normalizeScale(null, 1.5f), 0f)
        assertEquals(1f, SuperWallpaperClockPolicy.normalizeScale(null, -2f), 0f)
        assertEquals(1f, SuperWallpaperClockPolicy.normalizeScale(Float.NaN), 0f)
    }

    @Test
    fun mapsStaticClockRatioIntoSuperWallpaperRange() {
        assertEquals(
            0.5f,
            SuperWallpaperClockPolicy.mapStaticTopRatioToSuperPosition(0.5f, 0.2f, 0.8f)!!,
            0.0001f,
        )
        assertEquals(
            0.0f,
            SuperWallpaperClockPolicy.mapStaticTopRatioToSuperPosition(0.2f, 0.2f, 0.8f)!!,
            0.0001f,
        )
        assertEquals(
            1.0f,
            SuperWallpaperClockPolicy.mapStaticTopRatioToSuperPosition(0.8f, 0.2f, 0.8f)!!,
            0.0001f,
        )
        assertEquals(null, SuperWallpaperClockPolicy.mapStaticTopRatioToSuperPosition(0.5f, 1f, 1f))
    }

    @Test
    fun depthSafeBoundsClampOnlyWhenBoundsAreAvailable() {
        val bounds = SuperWallpaperClockPolicy.DepthSafeBounds(0.2f, 0.7f)
        assertEquals(0.2f, SuperWallpaperClockPolicy.clampToDepthSafeBounds(0.1f, bounds), 0f)
        assertEquals(0.7f, SuperWallpaperClockPolicy.clampToDepthSafeBounds(0.9f, bounds), 0f)
        assertEquals(0.9f, SuperWallpaperClockPolicy.clampToDepthSafeBounds(0.9f, null), 0f)
    }

    @Test
    fun depthBoundsConvertSystemDpToScreenRatio() {
        assertEquals(
            0.125f,
            SuperWallpaperClockPolicy.normalizeDepthBound(100f, 2400, 3f)!!,
            0.0001f,
        )
        assertEquals(
            0.5f,
            SuperWallpaperClockPolicy.normalizeDepthBound(0.5f, 2400, 3f)!!,
            0.0001f,
        )
    }

    @Test
    fun colorDataMapsPrimarySecondaryAndExtraColorsWithoutEnablingDepth() {
        val target = ColorDataFixture(
            primary = 0xff102030.toInt(),
            secondary = 0xff405060.toInt(),
            extra1 = 0xff708090.toInt(),
            extra2 = 0xffa0b0c0.toInt(),
        )

        val values = SuperWallpaperClockPolicy.readClockValuesFrom(target)

        assertEquals(0xff102030.toInt(), values?.primaryColor)
        assertEquals(0xff405060.toInt(), values?.secondaryColor)
        assertEquals(0xff708090.toInt(), values?.blendColor)
        assertEquals(0xffa0b0c0.toInt(), values?.secondaryBlendColor)
    }

    @Test
    fun automaticColorDataUsesPaletteInsteadOfZeroSentinels() {
        val target = ColorDataFixture(
            primary = 0,
            secondary = 0,
            extra1 = 0,
            extra2 = 0,
            palette = mapOf("secondary85" to 0xffeeeeee.toInt(), "secondary15" to 0xff222222.toInt()),
        )

        val values = SuperWallpaperClockPolicy.readClockValuesFrom(target)

        assertEquals(0xffeeeeee.toInt(), values?.primaryColor)
        assertEquals(0xffeeeeee.toInt(), values?.secondaryColor)
        assertEquals(0xff222222.toInt(), values?.blendColor)
        assertEquals(0xff222222.toInt(), values?.secondaryBlendColor)
    }

    @Test
    fun glassDiffusionAloneDoesNotEnterDepthPath() {
        val values = SuperWallpaperClockPolicy.ClockValues(diffusion = true)
        assertFalse(SuperWallpaperClockPolicy.isDepthValue(values))
    }

    @Test
    fun staticConfigurationIsNotMarkedAsSuperWallpaper() {
        val config = CommonConfig(LockscreenInfo(WallpaperInfo("image")))

        assertFalse(SuperWallpaperClockPolicy.isSuperWallpaperConfig(config))
        assertFalse(SuperWallpaperClockPolicy.isSuperWallpaperTarget(config))
        assertFalse(SuperWallpaperClockPolicy.isDepthWallpaperConfig(config))
    }

    @Test
    fun recognizesSystemUiCurrentJsonAndCategoryNameMarkers() {
        assertTrue(
            SuperWallpaperClockPolicy.isSuperWallpaperTarget(
                CurrentJsonFixture("{\"name\":\"super_wallpaper\"}"),
            ),
        )
        assertTrue(
            SuperWallpaperClockPolicy.isDepthWallpaperTarget(
                CurrentJsonFixture("{\"isDepthVideo\":true}"),
            ),
        )
        assertTrue(
            SuperWallpaperClockPolicy.isSuperWallpaperTarget(CategoryNameFixture("blank_super_wallpaper")),
        )
        assertFalse(
            SuperWallpaperClockPolicy.isSuperWallpaperTarget(CurrentJsonFixture("{\"name\":\"image\"}")),
        )
    }

    @Test
    fun explicitClockDepthFlagIsAcceptedWithoutMarkingStaticWallpaperSuper() {
        val target = ExplicitDepthFixture(2)

        assertFalse(SuperWallpaperClockPolicy.isSuperWallpaperTarget(target))
        assertTrue(SuperWallpaperClockPolicy.isDepthWallpaperTarget(target))
        assertTrue(SuperWallpaperClockPolicy.shouldForceWallpaperDepth(target))
    }

    @Test
    fun repairsControllerDepthCacheAndLiveClockView() {
        val target = DepthControllerFixture()

        assertTrue(SuperWallpaperClockPolicy.forceWallpaperDepth(target))
        assertEquals(1, target.mSetWallpaperSupportDepth)
        assertTrue(target.wallpaperSupportDepth)
        assertTrue(target.mClockView.depth)
    }

    private class CommonConfig(private val lockscreenInfo: LockscreenInfo) {
        fun getLockscreenInfo(): LockscreenInfo = lockscreenInfo
    }

    private class LockscreenInfo(private val wallpaperInfo: WallpaperInfo) {
        fun getWallpaperInfo(): WallpaperInfo = wallpaperInfo
    }

    private class WallpaperInfo(private val resourceType: String) {
        fun getResourceType(): String = resourceType
    }

    private class ClockBean(
        val primaryColor: Int,
        val secondaryColor: Int,
        val timeWidth: Float,
        val timeHeight: Float,
        val clockTopRatio: Float,
        val clockDepthType: Int,
        val enableDiffusion: Boolean,
    )

    private class ColorDataFixture(
        private val primary: Int,
        private val secondary: Int,
        private val extra1: Int,
        private val extra2: Int,
        private val palette: Map<String, Int> = emptyMap(),
    ) {
        fun getPrimaryColor(): Int = primary
        fun getSecondaryColor(): Int = secondary
        fun getExtraColor1(): Int = extra1
        fun getExtraColor2(): Int = extra2
        fun getClockPalette(): Map<String, Int> = palette
    }

    private class CurrentJsonFixture(private val currentJson: String) {
        fun getCurrentJson(): String = currentJson
    }

    private class CategoryNameFixture(private val name: String) {
        fun getName(): String = name
    }

    private class ExplicitDepthFixture(private val clockDepthType: Int) {
        fun getClockDepthType(): Int = clockDepthType
    }

    private class DepthControllerFixture {
        var mSetWallpaperSupportDepth: Int = 2
        var wallpaperSupportDepth: Boolean = false
        val mClockView = DepthClockViewFixture()
    }

    private class DepthClockViewFixture {
        var depth: Boolean = false

        fun setWallpaperSupportDepth(value: Boolean) {
            depth = value
        }
    }
}
