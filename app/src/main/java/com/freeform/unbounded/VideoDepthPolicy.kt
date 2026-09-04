package com.freeform.unbounded

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.View
import java.lang.reflect.Modifier

/**
 * Adapter for the depth implementation already shipped with the clock library.
 *
 * Dynamic video wallpapers and Super wallpapers are rendered by different
 * applications, but SystemUI owns the same depth-avoidance evaluator for both.
 * Keeping this bridge reflection based lets the module work across vendor dex
 * revisions without linking against a private MIUI class at install time.
 */
internal object VideoDepthPolicy {
    private const val DEPTH_UTILS = "com.miui.clock.utils.avoid.ClockDepthAvoidRuleUtils"
    private const val SUPER_WALLPAPER_SETTING = "aod_using_super_wallpaper"

    private val superTypeMarkers = setOf(
        "super",
        "super_wallpaper",
        "superwallpaper",
        "blank_super_wallpaper",
        "aod_using_super_wallpaper",
    )

    data class SafeBounds(val top: Float, val bottom: Float)

    /** Detects the currently selected Super wallpaper from a SystemUI object. */
    fun isSuperWallpaperTarget(target: Any?): Boolean {
        val value = target ?: return false
        val className = value.javaClass.name.lowercase()
        if (className.contains("superwallpaper") || className.contains("super_wallpaper")) {
            return true
        }
        val marker = readObject(value, "getName", "name", "mName", "getCategoryName", "categoryName")
            ?.toString()
            ?.lowercase()
        if (marker != null && superTypeMarkers.any(marker::contains)) return true
        if (readObject(value, "isSuperWallpaper", "getIsSuperWallpaper", "isSupportSuperWallpaper") == true) {
            return true
        }
        val json = readObject(value, "getCurrentJson", "currentJson", "mCurrentJson")?.toString()?.lowercase()
        if (json != null && superTypeMarkers.any(json::contains)) return true

        val context = findContext(value)
        return context?.let(::isSuperWallpaperSetting) == true
    }

    fun shouldUseVideoDepth(target: Any?, enabled: Boolean): Boolean =
        enabled && isSuperWallpaperTarget(target)

    /**
     * Applies the same safe-area calculation used by dynamic video wallpaper
     * clocks and repairs the controller/view cache after a stock update.
     */
    fun applyVideoDepth(target: Any?): SafeBounds? {
        val receiver = target ?: return null
        writeMember(receiver, 1, "mSetWallpaperSupportDepth")
        writeMember(receiver, true, "wallpaperSupportDepth", "mWallpaperSupportDepth")

        val clockView = readObject(receiver, "mClockView", "clockView", "getClockView")
        if (clockView != null) {
            // The controller delegates the state to its active clock view.
            invokeBooleanSetter(clockView, "setWallpaperSupportDepth", true)
        }

        val bounds = readCurrentSafeBounds(receiver)
        if (bounds != null) {
            applyBounds(receiver, bounds)
        }
        return bounds
    }

    /** Exposed for deterministic unit tests and compatibility diagnostics. */
    internal fun normalizeBound(value: Float, displayHeight: Int, density: Float): Float? {
        if (!value.isFinite() || displayHeight <= 0 || !density.isFinite() || density <= 0f) return null
        if (value in -0.25f..1.25f) return value.coerceIn(0f, 1f)
        return (value * density / displayHeight.toFloat()).takeIf(Float::isFinite)?.coerceIn(0f, 1f)
    }

    private fun readCurrentSafeBounds(target: Any): SafeBounds? = runCatching {
        val context = findContext(target) ?: return@runCatching null
        val loaders = listOfNotNull(
            target.javaClass.classLoader,
            context.javaClass.classLoader,
            Thread.currentThread().contextClassLoader,
        ).distinct()
        var safeBounds: Any? = null
        for (loader in loaders) {
            val utilsClass = runCatching { Class.forName(DEPTH_UTILS, false, loader) }.getOrNull() ?: continue
            val method = utilsClass.methods.firstOrNull {
                it.name == "getCurrentSafeBounds" && it.parameterTypes.contentEquals(arrayOf(Context::class.java))
            } ?: continue
            val receiver = if (Modifier.isStatic(method.modifiers)) {
                null
            } else {
                utilsClass.fields.firstOrNull { it.name == "INSTANCE" }
                    ?.let { field -> runCatching { field.get(null) }.getOrNull() }
            }
            safeBounds = runCatching { method.invoke(receiver, context) }.getOrNull()
            if (safeBounds != null) break
        }
        val top = readNumber(safeBounds, "getTop", "top")?.toFloat() ?: return@runCatching null
        val bottom = readNumber(safeBounds, "getBottom", "bottom")?.toFloat() ?: return@runCatching null
        val height = displayHeight(target, context)
        val density = context.resources.displayMetrics.density
        val normalizedTop = normalizeBound(top, height, density) ?: return@runCatching null
        val normalizedBottom = normalizeBound(bottom, height, density) ?: return@runCatching null
        SafeBounds(normalizedTop, normalizedBottom)
    }.getOrNull()

    private fun applyBounds(target: Any, bounds: SafeBounds) {
        val height = displayHeight(target, findContext(target) ?: return)
        if (height <= 0) return
        val top = (bounds.top * height).toInt().coerceIn(0, height)
        val bottom = (bounds.bottom * height).toInt().coerceIn(top, height)
        val candidates = listOfNotNull(
            target,
            readObject(target, "mClockView", "clockView", "getClockView"),
        )
        candidates.forEach { candidate ->
            writeMember(candidate, top, "mTopMin")
            writeMember(candidate, bottom, "mBottomMax")
            writeMember(candidate, Point(top, bottom), "mTopAndBottom")
        }
    }

    private fun findContext(target: Any): Context? =
        readObject(target, "mContext", "context", "getContext") as? Context

    private fun displayHeight(target: Any, context: Context): Int {
        val viewHeight = (target as? View)?.height ?: 0
        return if (viewHeight > 0) viewHeight else context.resources.displayMetrics.heightPixels
    }

    private fun isSuperWallpaperSetting(context: Context): Boolean = runCatching {
        android.provider.Settings.Secure.getInt(
            context.contentResolver,
            SUPER_WALLPAPER_SETTING,
            0,
        ) == 1
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
        false
    }.getOrDefault(false)

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

    private fun readObject(target: Any?, vararg names: String): Any? {
        val value = target ?: return null
        var type: Class<*>? = value.javaClass
        while (type != null) {
            val method = type.declaredMethods.firstOrNull { it.name in names && it.parameterTypes.isEmpty() }
            val field = type.declaredFields.firstOrNull { it.name in names }
            if (method != null || field != null) {
                return runCatching {
                    method?.isAccessible = true
                    field?.isAccessible = true
                    method?.invoke(value) ?: field?.get(value)
                }.getOrNull()
            }
            type = type.superclass
        }
        return null
    }

    private fun readNumber(target: Any?, vararg names: String): Number? =
        readObject(target, *names) as? Number
}
