package com.freeform.unbounded

import android.graphics.Point
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.roundToInt

/**
 * The AOD application has a second clock implementation for Super wallpapers.
 * This object is deliberately reflection based: the implementation moved between
 * the AOD and SystemUI dex files several times across HyperOS releases.
 */
internal object SuperWallpaperClockPolicy {
    private val staticWallpaperTypes = setOf("image", "gallery", "bitmap", "photo", "static")
    private val dynamicWallpaperTypes = setOf(
        "video", "dynamic", "live", "live_photo", "livephoto", "depth", "depth_video",
        "super", "super_wallpaper", "superwallpaper", "super-wallpaper",
    )
    private val superWallpaperJsonMarkers = setOf(
        "super_wallpaper", "superwallpaper", "blank_super_wallpaper", "aod_using_super_wallpaper",
    )
    private val depthWallpaperJsonMarkers = setOf(
        "depth_video", "isdepthvideo", "deep_camera", "wallpaper_matting_support_2",
    )
    @Volatile
    private var rememberedValues: ClockValues? = null

    data class ClockValues(
        val primaryColor: Int? = null,
        val secondaryColor: Int? = null,
        val blendColor: Int? = null,
        val secondaryBlendColor: Int? = null,
        val timeWidth: Float? = null,
        val timeHeight: Float? = null,
        val clockTopRatio: Float? = null,
        val clockBottomRatio: Float? = null,
        val clockGravity: Int? = null,
        val depthType: Int? = null,
        val effect: Int? = null,
        val glassTransparency: Float? = null,
        val diffusion: Boolean? = null,
    )

    data class DepthSafeBounds(
        val top: Float,
        val bottom: Float,
    )

    fun isSuperWallpaperResourceType(resourceType: String?): Boolean {
        val normalized = resourceType?.trim()?.lowercase().orEmpty()
        return normalized in setOf("super", "super_wallpaper", "superwallpaper", "super-wallpaper") ||
            normalized.contains("super")
    }

    fun isDynamicResourceType(resourceType: String?): Boolean {
        val normalized = resourceType?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty() || normalized in staticWallpaperTypes) return false
        return normalized in dynamicWallpaperTypes || normalized.contains("video") ||
            normalized.contains("live") || normalized.contains("super") || normalized.contains("depth")
    }

    fun isDepthWallpaperResourceType(resourceType: String?): Boolean {
        val normalized = resourceType?.trim()?.lowercase().orEmpty()
        return normalized.contains("depth") || normalized.contains("super")
    }

    fun isSuperWallpaperConfig(config: Any?): Boolean {
        if (config is String) return isSuperWallpaperResourceType(config)
        config.readString("getResourceType", "resourceType", "getWallpaperType", "wallpaperType")?.let {
            if (isSuperWallpaperResourceType(it)) return true
        }
        val wallpaper = config.findWallpaperInfo() ?: return false
        val resourceType = wallpaper.readString("getResourceType", "resourceType", "getType", "type")
        return isSuperWallpaperResourceType(resourceType) ||
            wallpaper.readBoolean("isSuperWallpaper", "getIsSuperWallpaper") == true
    }

    fun isDepthWallpaperConfig(config: Any?): Boolean {
        if (config is String) return isDepthWallpaperResourceType(config)
        config.readString("getResourceType", "resourceType", "getWallpaperType", "wallpaperType")?.let {
            if (isDepthWallpaperResourceType(it)) return true
        }
        val wallpaper = config.findWallpaperInfo() ?: return false
        val resourceType = wallpaper.readString("getResourceType", "resourceType", "getType", "type")
        return isDepthWallpaperResourceType(resourceType) ||
            wallpaper.readBoolean("isDepthVideo", "getIsDepthVideo", "isDeepCameraWallpaper") == true
    }

    /** Identifies a hook receiver or one of its configuration objects as Super wallpaper. */
    fun isSuperWallpaperTarget(target: Any?): Boolean {
        val value = target ?: return false
        val className = value.javaClass.simpleName.lowercase()
        if (className.contains("superwallpaper") || className.contains("super_wallpaper")) return true
        if (value.readString(
                "getName", "name", "mName", "getCategoryName", "categoryName",
                "getClockStyleType", "clockStyleType",
            )?.let(::isSuperWallpaperResourceType) == true
        ) return true
        if (value.readBoolean(
                "isSupportSuperWallpaper", "isSuperWallpaper",
            ) == true
        ) return true
        if (value.readString("getCurrentJson", "currentJson", "mCurrentJson")
                ?.let(::containsSuperWallpaperMarker) == true
        ) return true
        if (isSuperWallpaperSetting(value)) return true
        if (isSuperWallpaperConfig(value)) return true
        val nestedNames = arrayOf(
            "mTemplateConfig", "templateConfig", "getTemplateConfig",
            "mCommonConfig", "commonConfig", "getCommonConfig",
            "wallpaperTypeInfo", "getWallpaperTypeInfo", "wallpaperInfo", "getWallpaperInfo",
            "mStyleInfo", "styleInfo", "getStyleInfo",
            "mClockBean", "clockBean", "getClockBean",
            "currentClockBean", "getCurrentClockBean",
            "mCurrentJson", "currentJson", "getCurrentJson",
        )
        return nestedNames.asSequence()
            .mapNotNull { value.readObject(it) }
            .any { nested ->
                isSuperWallpaperConfig(nested) ||
                    nested.javaClass.simpleName.lowercase().contains("superwallpaper") ||
                    (nested as? String)?.let(::containsSuperWallpaperMarker) == true ||
                    nested.readString(
                        "getCategoryName", "categoryName", "getName", "name", "mName",
                        "getClockStyleType", "clockStyleType",
                    )
                        ?.let(::isSuperWallpaperResourceType) == true
            }
    }

    /** Returns true when a Context belongs to the currently selected Super wallpaper. */
    fun isSuperWallpaperContext(context: Any?): Boolean =
        context is android.content.Context && isSuperWallpaperSetting(context)

    /** Forces MIUI's one-argument style lookups through the dedicated Super style factory. */
    fun forceSuperWallpaperStyleResult(
        methodName: String,
        arguments: Array<Any?>,
        stockResult: Any?,
    ): Any? {
        if (methodName != "getStyleInfo" && methodName != "getClockStyleInfo") return stockResult
        val context = arguments.firstOrNull { it is android.content.Context } ?: return stockResult
        if (!isSuperWallpaperContext(context)) return stockResult
        // Context implementations normally come from the framework class loader,
        // while AODStyleController lives in the AOD application loader. Try all
        // loaders available to this process instead of assuming they are shared.
        val loaders = listOfNotNull(
            context.javaClass.classLoader,
            Thread.currentThread().contextClassLoader,
            SuperWallpaperClockPolicy::class.java.classLoader,
        ).distinct()
        val controller = loaders.asSequence()
            .mapNotNull { loader -> runCatching {
                Class.forName("com.miui.aod.AODStyleController", false, loader)
            }.getOrNull() }
            .firstOrNull() ?: return stockResult
        val styleMethod = controller.methods.firstOrNull { method ->
            method.name == "getStyleInfo" && method.parameterTypes.size == 2 &&
                android.content.Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.parameterTypes[1] == String::class.java
        } ?: return stockResult
        val receiver = if (Modifier.isStatic(styleMethod.modifiers)) {
            null
        } else {
            controller.fields.firstOrNull { it.name == "INSTANCE" }
                ?.let { field -> runCatching { field.get(null) }.getOrNull() }
        }
        return runCatching { styleMethod.invoke(receiver, context, "super_wallpaper") ?: stockResult }
            .getOrDefault(stockResult)
    }

    fun isDepthWallpaperTarget(target: Any?): Boolean {
        val value = target ?: return false
        if (isDepthWallpaperConfig(value)) return true
        if (value.javaClass.simpleName.lowercase().contains("depth")) return true
        if (value.readString("getCurrentJson", "currentJson", "mCurrentJson")
                ?.let(::containsDepthWallpaperMarker) == true
        ) return true
        if (hasExplicitClockDepth(value)) return true
        return arrayOf(
            "mTemplateConfig", "templateConfig", "getTemplateConfig",
            "mCommonConfig", "commonConfig", "mClockBean", "clockBean", "getClockBean",
        ).mapNotNull { value.readObject(it) }.any { nested ->
            isDepthWallpaperConfig(nested) || hasExplicitClockDepth(nested)
        }
    }

    /** The SystemUI depth gate should be opened only for an identified Super/depth target. */
    fun shouldForceWallpaperDepth(target: Any?): Boolean =
        isSuperWallpaperTarget(target) || isDepthWallpaperTarget(target)

    /** Repairs MIUI's cached depth flags and forwards the state to the live clock view. */
    fun forceWallpaperDepth(target: Any?): Boolean {
        val receiver = target ?: return false
        var repaired = false
        repaired = writeMember(receiver, 1, "mSetWallpaperSupportDepth") || repaired
        repaired = writeMember(receiver, true, "wallpaperSupportDepth", "mWallpaperSupportDepth") || repaired

        val clockView = receiver.readObject("mClockView", "clockView", "getClockView")
        if (clockView != null && invokeBooleanSetter(clockView, "setWallpaperSupportDepth", true)) {
            repaired = true
        }
        return repaired
    }

    /**
     * Converts the static editor's screen ratio into Super wallpaper's 0..1
     * position space. The stock implementation subsequently maps this value
     * through sRangeTopRate/sRangeBottomRate, so the conversion deliberately
     * does not clamp the result; values outside the stock range are valid for
     * an unrestricted editor position.
     */
    fun mapStaticTopRatioToSuperPosition(
        topRatio: Float,
        rangeTop: Float,
        rangeBottom: Float,
    ): Float? {
        if (!topRatio.isFinite() || !rangeTop.isFinite() || !rangeBottom.isFinite()) return null
        val span = rangeBottom - rangeTop
        if (!span.isFinite() || kotlin.math.abs(span) < 0.0001f) return null
        return (topRatio - rangeTop) / span
    }

    /** Clamps an editor ratio to the host's depth-safe interval when present. */
    fun clampToDepthSafeBounds(topRatio: Float, bounds: DepthSafeBounds?): Float {
        if (!topRatio.isFinite() || bounds == null) return topRatio
        val top = bounds.top.takeIf { it.isFinite() } ?: return topRatio
        val bottom = bounds.bottom.takeIf { it.isFinite() } ?: return topRatio
        val lower = minOf(top, bottom)
        val upper = maxOf(top, bottom)
        return topRatio.coerceIn(lower, upper)
    }

    fun readClockValues(bean: Any?): ClockValues = ClockValues(
        primaryColor = bean.readInt("getPrimaryColor", "primaryColor"),
        secondaryColor = bean.readInt("getSecondaryColor", "secondaryColor"),
        blendColor = bean.readInt("getBlendColor", "blendColor")
            ?: bean.readInt("getExtraColor1", "extraColor1"),
        secondaryBlendColor = bean.readInt("getSecondaryBlendColor", "secondaryBlendColor")
            ?: bean.readInt("getExtraColor2", "extraColor2"),
        timeWidth = bean.readFloat("getTimeWidth", "timeWidth"),
        timeHeight = bean.readFloat("getTimeHeight", "timeHeight"),
        clockTopRatio = bean.readFloat("getClockTopRatio", "clockTopRatio"),
        clockBottomRatio = bean.readFloat("getClockBottomRatio", "clockBottomRatio"),
        clockGravity = bean.readInt("getClockGravity", "clockGravity", "gravity"),
        depthType = readDepthType(bean),
        effect = bean.readInt("getClockEffect", "clockEffect", "effect"),
        glassTransparency = bean.readFloat("getGlassTransparency", "glassTransparency"),
        diffusion = bean.readBoolean("isEnableDiffusion", "getEnableDiffusion", "enableDiffusion"),
    )

    /**
     * Replays the editor's live ColorData update for a Super wallpaper clock.
     * ColorData has no width/position information, so only the color portion
     * of the remembered clock state is replaced here.
     */
    fun applyColorData(target: Any?, colorData: Any?): Int {
        val receiver = target ?: return 0
        val values = readColorDataValues(colorData) ?: return 0
        val previous = rememberedValues
        val targetValues = readClockValuesFrom(receiver)
        val merged = ClockValues(
            primaryColor = values.primaryColor ?: previous?.primaryColor,
            secondaryColor = values.secondaryColor ?: previous?.secondaryColor,
            blendColor = values.blendColor ?: previous?.blendColor,
            secondaryBlendColor = values.secondaryBlendColor ?: previous?.secondaryBlendColor,
            timeWidth = targetValues?.timeWidth ?: previous?.timeWidth,
            timeHeight = targetValues?.timeHeight ?: previous?.timeHeight,
            clockTopRatio = targetValues?.clockTopRatio ?: previous?.clockTopRatio,
            clockBottomRatio = targetValues?.clockBottomRatio ?: previous?.clockBottomRatio,
            clockGravity = targetValues?.clockGravity ?: previous?.clockGravity,
            depthType = targetValues?.depthType ?: previous?.depthType,
            effect = targetValues?.effect ?: previous?.effect,
            glassTransparency = targetValues?.glassTransparency ?: previous?.glassTransparency,
            diffusion = targetValues?.diffusion ?: previous?.diffusion,
        )
        rememberedValues = merged
        if (!isSuperWallpaperTarget(receiver)) return 0
        return applyClockValues(receiver, merged, applyPosition = false)
    }

    /**
     * Captures a full static editor bean before the Super clock is inflated.
     * This is intentionally separate from applyStyleInfo: onColorPickComplete
     * is dispatched by BaseTemplateConfig for both static and Super templates.
     */
    fun rememberClockData(target: Any?, data: Any?): Int {
        val values = readClockValuesFrom(data)?.remember()
            ?: readClockValuesFrom(target)?.remember()
            ?: return 0
        if (!isSuperWallpaperTarget(target)) return 0
        return applyClockValues(target, values, applyPosition = false)
    }

    private fun readColorDataValues(colorData: Any?): ClockValues? {
        val data = colorData ?: return null
        val primary = data.readInt("getPrimaryColor", "primaryColor")
        val secondary = data.readInt("getSecondaryColor", "secondaryColor")
        val extra1 = data.readInt("getExtraColor1", "extraColor1")
        val extra2 = data.readInt("getExtraColor2", "extraColor2")
        val palette = data.readObject("getClockPalette", "clockPalette")
        if (primary == null && secondary == null && extra1 == null && extra2 == null && palette == null) {
            return null
        }
        val paletteMap = palette as? kotlin.collections.Map<*, *>
        val lightPalette = paletteMap?.firstPaletteColor(
            "secondary85", "secondary70", "secondary60", "secondary100", "primary100",
        )
        val darkPalette = paletteMap?.firstPaletteColor(
            "secondary15", "secondary30", "secondary40", "secondary0", "primary0",
        )
        // AutoColorPicker uses zero as a sentinel and stores the actual colors
        // in the palette. Preserve explicit non-zero custom colors, while
        // selecting the same light/dark palette tones used by MIUI's renderer
        // when the editor has produced an automatic ColorData object.
        val primaryValue = primary?.takeIf { it != 0 } ?: lightPalette
        val secondaryValue = secondary?.takeIf { it != 0 } ?: lightPalette ?: primaryValue
        return ClockValues(
            primaryColor = primaryValue,
            secondaryColor = secondaryValue,
            blendColor = extra1?.takeIf { it != 0 } ?: darkPalette,
            secondaryBlendColor = extra2?.takeIf { it != 0 } ?: darkPalette,
        )
    }

    private fun kotlin.collections.Map<*, *>.firstPaletteColor(vararg keys: String): Int? =
        keys.asSequence().mapNotNull { key -> (this[key] as? Number)?.toInt() }.firstOrNull()

    /** Reads either ClockBean fields or the color/gravity accessors exposed by StyleInfo. */
    fun readClockValuesFrom(target: Any?): ClockValues? {
        val receiver = target ?: return null
        val candidates = sequence {
            yield(receiver)
            val names = arrayOf(
                "currentClockBean", "getCurrentClockBean", "mClockBean", "clockBean", "getClockBean",
                "foreClockBean", "getForeClockBean", "mForeClockBean",
                "mStyleInfo", "styleInfo", "getStyleInfo", "mTemplateConfig", "templateConfig", "getTemplateConfig",
                // TemplateConfig is the bridge used by the static editor. Its
                // ClockInfo is copied into a ClockBean only by some templates,
                // so read it directly before relying on that copy.
                "mClockInfo", "clockInfo", "getClockInfo",
            )
            names.forEach { name -> receiver.readObject(name)?.let { yield(it) } }
        }.distinctBy { System.identityHashCode(it) }
        for (candidate in candidates) {
            if (candidate.readObject("getClockPalette", "clockPalette") != null &&
                candidate.readObject("getExtraColor1", "extraColor1") != null
            ) {
                readColorDataValues(candidate)?.let { return it }
            }
            val beanValues = readClockValues(candidate)
            if (beanValues.hasClockBeanValues()) return beanValues
            val styleValues = readStyleInfoValues(candidate)
            if (styleValues.hasClockBeanValues()) return styleValues
            candidate.readObject("getClockBean", "clockBean", "currentClockBean")?.let { bean ->
                val nested = readClockValues(bean)
                if (nested.hasClockBeanValues()) return nested
            }
            candidate.readObject("getClockInfo", "clockInfo", "mClockInfo")?.let { clockInfo ->
                val nested = readClockValues(clockInfo)
                if (nested.hasClockBeanValues()) return nested
            }
        }
        return null
    }

    private fun readStyleInfoValues(styleInfo: Any?): ClockValues = ClockValues(
        primaryColor = styleInfo.readInt("getClockColor", "clockColor", "mClockColor"),
        clockGravity = styleInfo.readInt("getClockGravity", "clockGravity", "gravity"),
    )

    /** ClockBean exposes Region as an enum; editor data beans expose its int value. */
    private fun readDepthType(target: Any?): Int? {
        val raw = target.readObject("getClockDepthType", "clockDepthType", "depthType")
        return when (raw) {
            is Number -> raw.toInt()
            is Enum<*> -> when (raw.name.uppercase()) {
                "REGION_1" -> 0
                "REGION_2" -> 1
                "REGION_3" -> 2
                else -> raw.ordinal
            }
            else -> null
        }
    }

    private fun ClockValues.hasClockBeanValues(): Boolean =
        primaryColor != null || secondaryColor != null || blendColor != null ||
            secondaryBlendColor != null || timeWidth != null || timeHeight != null ||
            clockTopRatio != null || clockBottomRatio != null || clockGravity != null ||
            depthType != null || effect != null || glassTransparency != null || diffusion != null

    /** Returns a stable, safe scale for values supplied by a template or slider. */
    fun normalizeScale(value: Float?, fallback: Float = 1f): Float {
        val safeFallback = fallback.takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(0.25f, 4f) ?: 1f
        return value.takeIf { it?.isFinite() == true && it > 0f }
            ?.coerceIn(0.25f, 4f) ?: safeFallback
    }

    /**
     * Applies values already computed by the stock static-wallpaper path to a
     * Super clock view. It intentionally leaves Glass untouched unless the bean
     * explicitly requests an effect, preserving the user's material choice.
     */
    fun applyClockValues(
        target: Any?,
        values: ClockValues,
        explicitSize: Int? = null,
        applyPosition: Boolean = true,
    ): Int {
        val receiver = target ?: return 0
        // setData(FFFFI) has already committed the native Super wallpaper
        // coordinates. When the caller asks for a visual-only pass, do not
        // feed remembered static-editor ratios back into those fields.
        if (applyPosition) syncCategoryPosition(receiver, values)
        if (isDepthValue(values)) applyDepthBounds(receiver)
        val roots = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
        val viewNames = IdentityHashMap<View, String>()
        collectViews(receiver, roots, viewNames)
        if (roots.isEmpty()) return 0
        val touched = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
        val defaultScale = when (explicitSize) {
            0 -> 0.82f
            1 -> 1f
            2 -> 1.18f
            else -> 1f
        }
        // ClockBean stores dimensions in dp on newer releases, while older
        // branches store normalized scale factors. Treat large values as
        // dimensions and derive the independent X scale from their ratio.
        val dimensionWidth = values.timeWidth?.takeIf { it.isFinite() && it > 8f }
        val dimensionHeight = values.timeHeight?.takeIf { it.isFinite() && it > 8f }
        val widthPx = dimensionWidth?.let { dimensionToPx(receiver, it) }
        val heightPx = dimensionHeight?.let { dimensionToPx(receiver, it) }
        val hasDimensions = widthPx != null || heightPx != null
        val widthScale = if (widthPx == null) normalizeScale(values.timeWidth, defaultScale) else 1f
        val heightScale = if (heightPx == null) normalizeScale(values.timeHeight, defaultScale) else 1f
        val color = values.primaryColor
        roots.forEach { root -> walk(root) { view ->
            if (!touched.add(view)) return@walk
            if (view is TextView) {
                val secondary = viewNames[view]?.lowercase()?.let { name ->
                    if (name.contains("2") || name.contains("secondary")) values.secondaryColor else null
                }
                (secondary ?: color)?.let { desiredColor ->
                    if (view.currentTextColor != desiredColor) view.setTextColor(desiredColor)
                }
                if (kotlin.math.abs(view.scaleX - widthScale) > 0.001f) view.scaleX = widthScale
                if (kotlin.math.abs(view.scaleY - heightScale) > 0.001f) view.scaleY = heightScale
                values.diffusion?.let { enabled ->
                    // MiuiTextGlassView exposes this setter; ordinary
                    // Super wallpaper TextViews simply ignore the call.
                    invokeBooleanSetter(view, "setEnableDiffusion", enabled)
                }
                applyTextGradient(view, values, secondary != null)
            }
            if (hasDimensions && viewNames[view]?.lowercase()?.contains("clockcontainer") == true) {
                (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    val currentWidth = maxOf(view.width, lp.width).takeIf { it > 0 }
                    val currentHeight = maxOf(view.height, lp.height).takeIf { it > 0 }
                    val width = widthPx?.roundToInt()?.coerceAtLeast(1)
                    val height = heightPx?.roundToInt()?.coerceAtLeast(1)
                    // The stock Super clock lays out text from resource dimensions;
                    // changing its LayoutParams alone is ignored on some releases.
                    // Scale the container around its top-left so position margins
                    // remain stable while width and height stay independently tunable.
                    view.pivotX = 0f
                    view.pivotY = 0f
                    if (width != null && currentWidth != null) {
                        val scaleX = width.toFloat() / currentWidth.toFloat()
                        if (kotlin.math.abs(view.scaleX - scaleX) > 0.001f) view.scaleX = scaleX
                    }
                    if (height != null && currentHeight != null) {
                        val scaleY = height.toFloat() / currentHeight.toFloat()
                        if (kotlin.math.abs(view.scaleY - scaleY) > 0.001f) view.scaleY = scaleY
                    }
                    if (view.width == 0 && view.height == 0 && (width != null || height != null)) {
                        if (width != null) lp.width = width
                        if (height != null) lp.height = height
                        view.layoutParams = lp
                    }
                }
            }
            if (values.clockGravity != null && view === root) {
                (view.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                    val gravity = values.clockGravity and Gravity.FILL
                    if (lp.gravity != gravity) {
                        lp.gravity = gravity
                        view.layoutParams = lp
                    }
                }
            }
        } }

        if (applyPosition && hasCategoryPositionFields(receiver)) {
            applyDirectPosition(receiver, roots, viewNames, values)
        }
        return touched.size
    }

    private fun hasCategoryPositionFields(target: Any): Boolean = listOf(
        "mClockPositionX", "mClockPositionY", "mDualClockPositionX", "mDualClockPositionY",
    ).any { target.hasField(it) }

    /** Applies the last static-editor values after a Super clock has been rebound. */
    fun applyClockFromTarget(
        target: Any?,
        explicitSize: Int? = null,
        applyPosition: Boolean = true,
    ): Int {
        if (!isSuperWallpaperTarget(target)) return 0
        val values = readClockValuesFrom(target)?.remember() ?: rememberedValues ?: return 0
        return applyClockValues(target, values, explicitSize, applyPosition)
    }

    /** Applies remembered category values to the view passed to SuperWallpaperCategoryInfo.setBg. */
    fun applyClockToView(category: Any?, view: Any?, explicitSize: Int? = null): Int {
        if (!isSuperWallpaperTarget(category) && !isSuperWallpaperTarget(view)) return 0
        val values = readClockValuesFrom(category)?.remember() ?: rememberedValues ?: return 0
        syncCategoryPosition(category, values, view)
        // setBg has already applied the category's translated margins. Only
        // reapply visual properties here; a second raw screen-ratio margin
        // would undo the stock sRangeTopRate/sRangeBottomRate conversion.
        return applyClockValues(view, values, explicitSize, applyPosition = false)
    }

    fun applyStyleInfo(target: Any?, styleInfo: Any?): Int {
        // Static editor callbacks often arrive on BaseTemplateConfig before
        // the SuperWallpaperClock exists. Remember the complete ClockInfo even
        // when that receiver is not itself identifiable as a Super target;
        // the later Super bind/update pass consumes the cached values.
        val values = readClockValuesFrom(styleInfo)?.remember()
            ?: readClockValuesFrom(target)?.remember()
            ?: rememberedValues
            ?: return 0
        if (!isSuperWallpaperTarget(target) && !isSuperWallpaperTarget(styleInfo)) return 0
        val preview = target.readObject(
            "mPreview", "preview", "getPreview", "mPreviewContainer", "previewContainer",
            "mAodContainerView", "aodContainerView",
        ) ?: target
        return applyClockValues(preview, values)
    }

    /** Applies the selected style to a BaseStyleSelectView preview after reinflation. */
    fun applyEditorPreview(target: Any?): Int {
        val receiver = target ?: return 0
        val styleInfo = receiver.readObject("mStyleInfo", "styleInfo", "getStyleInfo")
        return applyStyleInfo(receiver, styleInfo)
    }

    private fun ClockValues.remember(): ClockValues {
        val previous = rememberedValues
        return copy(
            primaryColor = primaryColor ?: previous?.primaryColor,
            secondaryColor = secondaryColor ?: previous?.secondaryColor,
            blendColor = blendColor ?: previous?.blendColor,
            secondaryBlendColor = secondaryBlendColor ?: previous?.secondaryBlendColor,
            timeWidth = timeWidth ?: previous?.timeWidth,
            timeHeight = timeHeight ?: previous?.timeHeight,
            clockTopRatio = clockTopRatio ?: previous?.clockTopRatio,
            clockBottomRatio = clockBottomRatio ?: previous?.clockBottomRatio,
            clockGravity = clockGravity ?: previous?.clockGravity,
            depthType = depthType ?: previous?.depthType,
            effect = effect ?: previous?.effect,
            glassTransparency = glassTransparency ?: previous?.glassTransparency,
            diffusion = diffusion ?: previous?.diffusion,
        ).also { rememberedValues = it }
    }

    /** Writes the static editor position into SuperWallpaperCategoryInfo's fields. */
    private fun syncCategoryPosition(target: Any?, values: ClockValues, boundsSource: Any? = null) {
        val receiver = target ?: return
        val hasCategoryPosition = listOf(
            "mClockPositionX", "mClockPositionY", "mDualClockPositionX", "mDualClockPositionY",
        ).any { receiver.hasField(it) }
        if (!hasCategoryPosition) return

        val bounds = if (isDepthValue(values)) readDepthSafeBounds(boundsSource ?: receiver) else null
        fun safeRatio(ratio: Float?): Float? = ratio?.let {
            if (bounds != null) clampToDepthSafeBounds(it, bounds) else it
        }
        val safeTop = safeRatio(values.clockTopRatio)
        val safeBottom = safeRatio(values.clockBottomRatio ?: values.clockTopRatio)
        val rangeTop = receiver.readFloat("sRangeTopRate", "getSRangeTopRate")
        val rangeBottom = receiver.readFloat("sRangeBottomRate", "getSRangeBottomRate")
        fun toSuperY(ratio: Float?): Float? = if (ratio != null && rangeTop != null && rangeBottom != null) {
            mapStaticTopRatioToSuperPosition(ratio, rangeTop, rangeBottom)
        } else {
            ratio
        }
        toSuperY(safeTop)?.takeIf { it.isFinite() }?.let { writeMember(receiver, it, "mClockPositionY") }
        toSuperY(safeBottom)?.takeIf { it.isFinite() }?.let { writeMember(receiver, it, "mDualClockPositionY") }

        val horizontalGravity = values.clockGravity?.and(Gravity.HORIZONTAL_GRAVITY_MASK)
        val superX = when (horizontalGravity) {
            Gravity.END -> 1f
            Gravity.CENTER_HORIZONTAL -> 0.5f
            Gravity.START -> 0f
            else -> null
        }
        superX?.let {
            writeMember(receiver, it, "mClockPositionX")
            writeMember(receiver, it, "mDualClockPositionX")
        }
    }

    /** Applies direct margins after stock Super wallpaper layout has reset them. */
    private fun applyDirectPosition(
        receiver: Any,
        roots: Set<View>,
        viewNames: Map<View, String>,
        values: ClockValues,
    ) {
        val requestedTop = values.clockTopRatio ?: return
        val bounds = if (isDepthValue(values)) readDepthSafeBounds(receiver) else null
        val safeTop = clampToDepthSafeBounds(requestedTop, bounds)
        val safeBottom = values.clockBottomRatio?.let { clampToDepthSafeBounds(it, bounds) }
        if (!safeTop.isFinite() || safeBottom?.isFinite() == false) return
        // SuperWallpaperCategoryInfo.translateYPosition maps its stored 0..1
        // value back into the screen ratio. The inverse mapping has already
        // been applied when syncing the category fields, so safeTop is the
        // translated screen ratio used by the stock margin formula.
        val positionRatio = safeTop
        val candidates = roots.asSequence()
            .filter { view ->
                val name = viewNames[view]?.lowercase().orEmpty()
                val hasMarginParams = view.layoutParams is ViewGroup.MarginLayoutParams
                hasMarginParams && name.contains("clockcontainer")
            }
            .toList()
        val fallbackCandidates = if (candidates.isNotEmpty()) candidates else roots.asSequence()
            .filter { view ->
                val name = viewNames[view]?.lowercase().orEmpty()
                view is ViewGroup && name.contains("clockview")
            }
            .toList()
        val positionViews = if (fallbackCandidates.isNotEmpty()) fallbackCandidates else {
            roots.filterIsInstance<ViewGroup>().filter { it.parent is View }
        }.filter { candidate ->
            // Avoid translating both an outer clock root and an inner container.
            positionViewsPlaceholder(fallbackCandidates, candidate)
        }
        positionViews.forEach { root ->
            val parentHeight = (root.parent as? View)?.height ?: return@forEach
            if (parentHeight <= 0) return@forEach
            val lp = root.layoutParams as? ViewGroup.MarginLayoutParams ?: return@forEach
            // SuperWallpaperCategoryInfo.setBg uses the translated position
            // directly as a fraction of the full wallpaper height. Keep this
            // exact conversion when a later relayout resets the margins.
            val name = viewNames[root]?.lowercase().orEmpty()
            val ratio = if (safeBottom != null &&
                (name.contains("2") || name.contains("secondary") || name.contains("dual"))
            ) safeBottom else safeTop
            val topMargin = (parentHeight * ratio.coerceIn(-1f, 2f)).toInt()
            if (lp.topMargin != topMargin) {
                lp.topMargin = topMargin
                root.layoutParams = lp
            }
        }
    }

    private fun positionViewsPlaceholder(candidates: List<View>, candidate: View): Boolean {
        return candidates.none { other -> other !== candidate && isAncestor(other, candidate) }
    }

    /**
     * SuperWallpaperClock uses plain TextViews, so the normal ClockStyleInfo
     * renderer never receives the editor's blend colors. Recreate that small
     * part of the renderer with a text shader when a blend color is explicit;
     * a missing blend value leaves any stock/material shader untouched.
     */
    private fun applyTextGradient(view: TextView, values: ClockValues, secondary: Boolean) {
        // Glass clocks own a shader/effect pipeline; never replace it with a
        // plain LinearGradient while replaying Super wallpaper values.
        if (view.javaClass.name.contains("MiuiTextGlassView")) return
        val end = (if (secondary) values.secondaryBlendColor else values.blendColor) ?: return
        val start = (if (secondary) values.secondaryColor else values.primaryColor) ?: return
        val width = view.width.coerceAtLeast(view.measuredWidth).coerceAtLeast(1).toFloat()
        val shader = LinearGradient(
            0f,
            0f,
            width,
            0f,
            start,
            end,
            Shader.TileMode.CLAMP,
        )
        view.paint.shader = shader
        view.invalidate()
    }

    private fun isAncestor(ancestor: View, child: View): Boolean {
        var parent = child.parent
        while (parent is View) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }

    private fun dimensionToPx(target: Any, value: Float): Float {
        val context = findContext(target) ?: return value
        val density = context.resources.displayMetrics.density
        return if (density.isFinite() && density > 0f) value * density else value
    }

    /**
     * SuperWallpaperClockView caches its depth region in three fields and
     * consults those fields from getTopAndBottom(). Update the cache after the
     * stock view has been rebound so the dynamic evaluator is actually used.
     */
    private fun applyDepthBounds(target: Any) {
        val candidates = sequence {
            yield(target)
            target.readObject("mClockView", "clockView", "getClockView")?.let { yield(it) }
        }
        candidates.forEach { candidate ->
            val bounds = readDepthSafeBounds(candidate) ?: return@forEach
            val context = findContext(candidate) ?: findContext(target) ?: return@forEach
            val height = displayHeight(candidate, context)
            if (height <= 0) return@forEach
            val top = (bounds.top * height).toInt().coerceIn(0, height)
            val bottom = (bounds.bottom * height).toInt().coerceIn(top, height)
            writeMember(candidate, top, "mTopMin")
            writeMember(candidate, bottom, "mBottomMax")
            writeMember(candidate, Point(top, bottom), "mTopAndBottom")
        }
    }

    internal fun isDepthValue(values: ClockValues): Boolean =
        values.depthType?.let { it > 0 } == true

    /** Reflectively reuses the host's depth evaluator without linking against its classes. */
    private fun readDepthSafeBounds(target: Any?): DepthSafeBounds? = runCatching {
        val receiver = target ?: return@runCatching null
        val context = findContext(receiver) ?: return@runCatching null
        val loaders = listOfNotNull(
            receiver.javaClass.classLoader,
            context.javaClass.classLoader,
            Thread.currentThread().contextClassLoader,
        ).distinct()
        val utilsName = "com.miui.clock.utils.avoid.ClockDepthAvoidRuleUtils"
        var safeBounds: Any? = null
        for (loader in loaders) {
            val utilsClass = runCatching { Class.forName(utilsName, false, loader) }.getOrNull() ?: continue
            val method = utilsClass.methods.firstOrNull {
                it.name == "getCurrentSafeBounds" && it.parameterTypes.size == 1
            } ?: continue
            val instance = utilsClass.fields.firstOrNull { it.name == "INSTANCE" }
                ?.let { field -> runCatching { field.get(null) }.getOrNull() }
            safeBounds = runCatching { method.invoke(instance, context) }.getOrNull()
            if (safeBounds != null) break
        }
        val top = safeBounds?.readFloat("getTop", "top") ?: return@runCatching null
        val bottom = safeBounds.readFloat("getBottom", "bottom") ?: return@runCatching null
        val displayHeight = displayHeight(receiver, context)
        val density = context.resources.displayMetrics.density
        val normalizedTop = normalizeDepthBound(top, displayHeight, density) ?: return@runCatching null
        val normalizedBottom = normalizeDepthBound(bottom, displayHeight, density) ?: return@runCatching null
        DepthSafeBounds(normalizedTop, normalizedBottom)
    }.getOrNull()

    private fun findContext(target: Any): android.content.Context? =
        target.readObject("mContext", "context", "getContext") as? android.content.Context

    private fun displayHeight(target: Any, context: android.content.Context): Int {
        val viewHeight = (target as? View)?.height ?: 0
        if (viewHeight > 0) return viewHeight
        return context.resources.displayMetrics.heightPixels
    }

    internal fun normalizeDepthBound(value: Float, displayHeight: Int, density: Float): Float? {
        if (!value.isFinite() || displayHeight <= 0 || !density.isFinite() || density <= 0f) return null
        // The HyperOS evaluator stores bounds in dp. Keep the small normalized
        // form as a compatibility fallback for older vendor branches.
        if (value in -0.25f..1.25f) return value.coerceIn(0f, 1f)
        return (value * density / displayHeight.toFloat()).takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    }

    /** Calls a base implementation when SuperWallpaperStyleSelectView overrides it with a no-op. */
    fun invokeBaseSlide(target: Any?, view: Any?, offset: Float): Boolean {
        val receiver = target ?: return false
        val parameter = view ?: return false
        var type: Class<*>? = receiver.javaClass.superclass
        while (type != null) {
            val method = type.declaredMethods.firstOrNull {
                it.name == "onSlide" && it.parameterTypes.size == 2 &&
                    View::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                    (it.parameterTypes[1] == Float::class.javaPrimitiveType ||
                        it.parameterTypes[1] == Float::class.java)
            }
            if (method != null) {
                return runCatching {
                    method.isAccessible = true
                    method.invoke(receiver, parameter, offset)
                    true
                }.getOrDefault(false)
            }
            type = type.superclass
        }
        return false
    }

    private fun walk(view: View, action: (View) -> Unit) {
        action(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) walk(view.getChildAt(index), action)
    }

    private fun collectViews(
        target: Any,
        output: MutableSet<View>,
        names: MutableMap<View, String>,
    ) {
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        collectViewsRecursively(target, output, names, visited)
    }

    /**
     * SuperWallpaperClock owns SuperWallpaperClockView, which in turn owns the
     * actual time/date/city TextViews. Walk those View-typed fields as well so
     * field names continue to distinguish the primary and secondary clocks.
     */
    private fun collectViewsRecursively(
        target: Any?,
        output: MutableSet<View>,
        names: MutableMap<View, String>,
        visited: MutableSet<Any>,
    ) {
        val receiver = target ?: return
        if (!visited.add(receiver)) return
        (receiver as? View)?.let(output::add)
        var type: Class<*>? = receiver.javaClass
        while (type != null) {
            type.declaredFields.forEach { field ->
                if (!View::class.java.isAssignableFrom(field.type)) return@forEach
                runCatching {
                    field.isAccessible = true
                    val child = field.get(receiver) as? View ?: return@runCatching
                    output.add(child)
                    names.putIfAbsent(child, field.name)
                    collectViewsRecursively(child, output, names, visited)
                }
            }
            type = type.superclass
        }
    }

    private fun Any?.findWallpaperInfo(): Any? {
        val lockscreen = readObject("getLockscreenInfo", "lockscreenInfo", "getLockScreenInfo")
        return lockscreen?.readObject(
            "getWallpaperInfo",
            "wallpaperInfo",
            "getWallpaperInfoForVideo",
            "wallpaperInfoForVideo",
        ) ?: readObject(
            "getWallpaperInfo",
            "wallpaperInfo",
            "getWallpaperInfoForVideo",
            "wallpaperInfoForVideo",
        )
    }

    private fun Any?.readObject(vararg names: String): Any? {
        val receiver = this ?: return null
        return receiver.findMember(names) { method, field ->
            when {
                method != null && method.parameterTypes.isEmpty() -> method.invoke(receiver)
                field != null -> field.get(receiver)
                else -> null
            }
        }
    }

    private fun Any?.readString(vararg names: String): String? = readObject(*names) as? String
    private fun Any?.readInt(vararg names: String): Int? = (readObject(*names) as? Number)?.toInt()
    private fun Any?.readFloat(vararg names: String): Float? = (readObject(*names) as? Number)?.toFloat()
    private fun Any?.readBoolean(vararg names: String): Boolean? = readObject(*names) as? Boolean

    private fun Any.hasField(name: String): Boolean {
        var type: Class<*>? = javaClass
        while (type != null) {
            if (type.declaredFields.any { it.name == name }) return true
            type = type.superclass
        }
        return false
    }

    private fun hasExplicitClockDepth(target: Any?): Boolean {
        val value = target ?: return false
        val depth = readDepthType(value)
        return depth != null && depth > 0
    }

    private fun containsSuperWallpaperMarker(json: String): Boolean {
        val normalized = json.lowercase()
        return superWallpaperJsonMarkers.any(normalized::contains)
    }

    private fun containsDepthWallpaperMarker(json: String): Boolean {
        val normalized = json.lowercase()
        return depthWallpaperJsonMarkers.any(normalized::contains) ||
            normalized.contains("\"depth\":true") || normalized.contains("\"depth_enabled\":true")
    }

    private fun isSuperWallpaperSetting(target: Any): Boolean {
        val context = if (target is android.content.Context) {
            target
        } else {
            target.readObject("mContext", "context", "getContext") ?: return false
        }
        val resolver = context.readObject("getContentResolver") ?: return false
        return readSecureInt(resolver, "aod_using_super_wallpaper") == 1
    }

    private fun readSecureInt(resolver: Any, key: String): Int? = runCatching {
        val secureClass = Class.forName("android.provider.Settings\$Secure")
        val method = secureClass.methods.firstOrNull {
            it.name == "getInt" && it.parameterTypes.contentEquals(
                arrayOf(
                    Class.forName("android.content.ContentResolver"),
                    String::class.java,
                    Int::class.javaPrimitiveType,
                ),
            )
        } ?: return@runCatching null
        (method.invoke(null, resolver, key, 0) as? Number)?.toInt()
    }.getOrNull()

    private fun writeMember(target: Any, value: Any, vararg names: String): Boolean = runCatching {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { it.name in names }
            if (field != null) {
                if (value is Int && field.type != Int::class.javaPrimitiveType && field.type != Int::class.java) {
                    return@runCatching false
                }
                if (value is Boolean && field.type != Boolean::class.javaPrimitiveType && field.type != Boolean::class.java) {
                    return@runCatching false
                }
                field.isAccessible = true
                field.set(target, value)
                return@runCatching true
            }
            type = type.superclass
        }
        false
    }.getOrDefault(false)

    private fun invokeBooleanSetter(target: Any, name: String, value: Boolean): Boolean = runCatching {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val method = type.declaredMethods.firstOrNull {
                it.name == name && it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))
            }
            if (method != null) {
                method.isAccessible = true
                method.invoke(target, value)
                return@runCatching true
            }
            type = type.superclass
        }
        target.javaClass.methods.firstOrNull {
            it.name == name && it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))
        }?.let {
            it.invoke(target, value)
            true
        } ?: false
    }.getOrDefault(false)

    private fun <T> Any?.findMember(
        names: Array<out String>,
        block: (Method?, Field?) -> T,
    ): T? {
        val receiver = this ?: return null
        var type: Class<*>? = receiver.javaClass
        while (type != null) {
            val method = type.declaredMethods.firstOrNull { it.name in names && it.parameterTypes.isEmpty() }
            val field = type.declaredFields.firstOrNull { it.name in names }
            if (method != null || field != null) {
                return runCatching {
                    method?.isAccessible = true
                    field?.isAccessible = true
                    block(method, field)
                }.getOrNull()
            }
            type = type.superclass
        }
        return null
    }
}
