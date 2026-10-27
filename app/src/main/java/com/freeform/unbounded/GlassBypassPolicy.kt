package com.freeform.unbounded

/**
 * Identifies the one stock rejection that blocks the Glass font effect on
 * dynamic and Super wallpapers. Reflection keeps the module independent from
 * the private AOD application's classes at compile time.
 */
internal object GlassBypassPolicy {
    private const val GLASS_EFFECT_ID = 5
    private const val ALL_IN_ONE_TEMPLATE = "all_in_one"
    private val STATIC_RESOURCE_TYPES = setOf("image", "gallery")
    // These all-in-one style/font pairs are present in the inspected AOD
    // preset_clock_template_config.json.  The runtime Glass marker fields
    // below distinguish them from ordinary clocks; keeping this list complete
    // prevents a valid preset from losing its Glass digits after a rebind.
    // The current device dump directly identifies this as the Glass clock:
    // style=5, fontStyle=22, hollowStyle=0, glassTransparency=0.15. Preset
    // membership alone is not enough to classify another all-in-one clock as
    // Glass, so unknown style/font pairs remain on the stock path.
    private val GLASS_STYLE_FONT_PAIRS = setOf(5 to 22)

    fun shouldOverride(
        effectId: Int,
        stockDisabled: Boolean,
        commonConfig: Any?,
    ): Boolean {
        if (!stockDisabled || effectId != GLASS_EFFECT_ID) return false

        val lockscreenInfo = commonConfig.invokeNoArg("getLockscreenInfo") ?: return false
        val clockInfo = lockscreenInfo.invokeNoArg("getClockInfo") ?: return false
        val templateId = clockInfo.invokeNoArg("getTemplateId") as? String ?: return false
        if (!isAllInOneTemplate(templateId)) return false

        val wallpaperInfo = lockscreenInfo.invokeNoArg("getWallpaperInfo") ?: return false
        val resourceType = wallpaperInfo.invokeNoArg("getResourceType") as? String ?: return false
        return isDynamicResourceType(resourceType)
    }

    fun shouldPreserveGlassEffect(effectId: Int, commonConfig: Any?): Boolean =
        effectId == GLASS_EFFECT_ID && isDynamicWallpaper(commonConfig) && isAllInOneConfig(commonConfig)

    fun shouldAllowGlassWallpaperFilter(types: Array<out String>?): Boolean =
        types.orEmpty().any(::isDynamicResourceType)

    fun shouldKeepGlassEnabled(target: Any?): Boolean {
        val templateConfig = target.invokeNoArg("templateConfig")
            ?: target.invokeNoArg("getTemplateConfig")
            ?: return false
        val clockInfo = templateConfig.invokeNoArg("getClockInfo") ?: return false
        val templateId = clockInfo.invokeNoArg("getTemplateId") as? String ?: return false
        if (!isAllInOneTemplate(templateId)) return false
        val wallpaperInfo = templateConfig.invokeNoArg("getWallpaperInfo") ?: return false
        val resourceType = wallpaperInfo.invokeNoArg("getResourceType") as? String ?: return false
        return isDynamicResourceType(resourceType)
    }

    /**
     * SystemUI receives a ClockBean after AOD has already downgraded Glass to
     * effect 1. The remaining Glass-specific fields and the depth-wallpaper
     * state provide a narrow, process-local signal for restoring effect 5.
     */
    fun shouldRestoreSystemUiGlass(controller: Any?, clockBean: Any?): Boolean {
        if (!isGlassDowngrade(clockBean)) return false
        // The settings reload path can downgrade the bean before the wallpaper
        // controller has rebuilt its depth state. The Glass marker set is the
        // narrower signal here; requiring depth made direct Glass-font use fail
        // during that normal rebind window.
        return controller != null
    }

    /**
     * The settings reload path may report depth=false even though the bean
     * still contains the complete Glass marker set. Restore that bean before
     * SystemUI applies it, otherwise a successful preview is immediately
     * replaced by effect 1 on the next settings refresh.
     */
    fun shouldRestorePersistedSystemUiGlass(clockBean: Any?): Boolean =
        isGlassDowngrade(clockBean)

    private fun isGlassDowngrade(clockBean: Any?): Boolean {
        val templateId = clockBean.invokeNoArg("getTemplateId") as? String ?: return false
        if (!isAllInOneTemplate(templateId)) return false
        val effect = (clockBean.invokeNoArg("getClockEffect") as? Number)?.toInt() ?: return false
        if (effect != 1) return false
        val style = (clockBean.invokeNoArg("getStyle") as? Number)?.toInt() ?: return false
        val fontStyle = (clockBean.invokeNoArg("getFontStyle") as? Number)?.toInt() ?: return false
        val hollowStyle = (clockBean.invokeNoArg("getHollowStyle") as? Number)?.toInt() ?: return false
        val transparency = (clockBean.invokeNoArg("getGlassTransparency") as? Number)?.toFloat()
            ?: return false
        // The wallpaper switch in the field log downgraded only clockEffect
        // to 1 and retained these Glass markers. Presets use hollowStyle=1;
        // keep 0 for the filled runtime variant.
        if ((style to fontStyle) !in GLASS_STYLE_FONT_PAIRS || hollowStyle !in setOf(0, 1) ||
            !transparency.isFinite() || transparency !in 0f..0.2f
        ) return false
        return true
    }

    private fun isDynamicWallpaper(commonConfig: Any?): Boolean {
        val lockscreenInfo = commonConfig.invokeNoArg("getLockscreenInfo") ?: return false
        val wallpaperInfo = lockscreenInfo.invokeNoArg("getWallpaperInfo") ?: return false
        val resourceType = wallpaperInfo.invokeNoArg("getResourceType") as? String ?: return false
        return isDynamicResourceType(resourceType)
    }

    private fun isAllInOneConfig(commonConfig: Any?): Boolean {
        val lockscreenInfo = commonConfig.invokeNoArg("getLockscreenInfo") ?: return false
        val clockInfo = lockscreenInfo.invokeNoArg("getClockInfo") ?: return false
        val templateId = clockInfo.invokeNoArg("getTemplateId") as? String ?: return false
        return isAllInOneTemplate(templateId)
    }

    private fun isAllInOneTemplate(templateId: String): Boolean =
        templateId == ALL_IN_ONE_TEMPLATE || templateId.startsWith("${ALL_IN_ONE_TEMPLATE}_")

    private fun isDynamicResourceType(resourceType: String): Boolean {
        val normalized = resourceType.trim().lowercase()
        if (normalized.isEmpty() || normalized in STATIC_RESOURCE_TYPES) return false
        return normalized in setOf(
            "video", "dynamic", "live", "live_photo", "livephoto",
            "super", "super_wallpaper", "superwallpaper", "depth", "depth_video",
        ) || normalized.contains("video") || normalized.contains("super")
    }

    private fun Any?.invokeNoArg(name: String): Any? = runCatching {
        val target = this ?: return@runCatching null
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val method = type.declaredMethods.firstOrNull {
                it.name == name && it.parameterTypes.isEmpty()
            }
            if (method != null) {
                method.isAccessible = true
                return@runCatching method.invoke(target)
            }
            val field = type.declaredFields.firstOrNull { it.name == name }
            if (field != null) {
                field.isAccessible = true
                return@runCatching field.get(target)
            }
            type = type.superclass
        }
        null
    }.getOrNull()
}
