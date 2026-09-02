package com.freeform.unbounded

import android.graphics.Rect
import java.lang.reflect.Method

internal enum class HookAction {
    DISABLE_BOOLEAN,
    FREE_HORIZONTAL_FRICTION,
    PRESERVE_HORIZONTAL_DRAG_SESSION,
    PRESERVE_HORIZONTAL_STABLE_OFFSET,
    PRESERVE_HORIZONTAL_ANIM_TARGET_PARAM,
    PRESERVE_HORIZONTAL_MOVE_FINAL_BOUNDS,
    ALLOW_GLASS_ON_ANY_WALLPAPER,
    PRESERVE_GLASS_EFFECT,
    ALLOW_GLASS_WALLPAPER_FILTER,
    SKIP_GLASS_FILTER_DISABLE,
    PRESERVE_GLASS_SYSTEMUI,
    /** Reuses the static-clock edit values in the Super wallpaper clock path. */
    ADAPT_SUPER_WALLPAPER_CLOCK,
    /** The Super wallpaper style selector currently overrides the base callback with a no-op. */
    DELEGATE_SUPER_WALLPAPER_SLIDE,
    /** Keeps the editor's hierarchy/depth capability available for Super wallpapers. */
    PRESERVE_SUPER_WALLPAPER_EDITOR,
    /** Keeps SystemUI's depth state enabled for Super/depth wallpapers. */
    PRESERVE_SUPER_WALLPAPER_DEPTH,
}

internal data class MethodHookRule(
    val names: Set<String>,
    val action: HookAction,
) {
    fun matches(method: Method): Boolean = matches(
        name = method.name,
        returnType = method.returnType.name,
        parameterTypes = method.parameterTypes.map { it.name },
    )

    fun matches(name: String, returnType: String, parameterTypes: List<String>): Boolean {
        if (name !in names) return false
        return when (action) {
            HookAction.DISABLE_BOOLEAN -> returnType == "boolean"
            HookAction.FREE_HORIZONTAL_FRICTION -> returnType == Rect::class.java.name &&
                parameterTypes == listOf(
                    "android.content.Context",
                    Rect::class.java.name,
                    Rect::class.java.name,
                    "float",
                    "android.graphics.PointF",
                    "boolean",
                )
            HookAction.PRESERVE_HORIZONTAL_DRAG_SESSION -> returnType == "void" && when (name) {
                "adjustBoundsAndScalePostUpdate" -> parameterTypes.size == 3 &&
                    parameterTypes[0] == "android.window.WindowContainerTransaction" &&
                    parameterTypes[1] == Rect::class.java.name
                "adjustFreeformBoundsAndScale" -> parameterTypes.size == 4 &&
                    parameterTypes[0].endsWith(".MiuiFreeformModeTaskInfo") &&
                    parameterTypes[1] == Rect::class.java.name &&
                    parameterTypes[2] == Rect::class.java.name &&
                    parameterTypes[3] == "float"
                else -> false
            }
            HookAction.PRESERVE_HORIZONTAL_STABLE_OFFSET -> returnType == "void" &&
                parameterTypes == listOf(Rect::class.java.name, Rect::class.java.name, "float")
            HookAction.PRESERVE_HORIZONTAL_ANIM_TARGET_PARAM -> returnType == "void" &&
                parameterTypes == listOf(Rect::class.java.name, "float", "float", "float")
            HookAction.PRESERVE_HORIZONTAL_MOVE_FINAL_BOUNDS -> returnType == Rect::class.java.name &&
                parameterTypes.size == 8 &&
                parameterTypes[0].endsWith(".MiuiFreeformModeTaskInfo") &&
                parameterTypes[1] == "int" &&
                parameterTypes[2] == "float" &&
                parameterTypes[3] == "float" &&
                parameterTypes[4] == "float" &&
                parameterTypes[5] == "float" &&
                parameterTypes[6] == "android.graphics.PointF" &&
                parameterTypes[7] == "float"
            HookAction.ALLOW_GLASS_ON_ANY_WALLPAPER -> returnType == "boolean" &&
                parameterTypes == listOf(
                    "int",
                    "com.miui.keyguard.editor.data.bean.CommonConfig",
                )
            HookAction.PRESERVE_GLASS_EFFECT -> returnType == "int" &&
                parameterTypes == listOf(
                    "com.miui.keyguard.editor.data.bean.CommonConfig",
                    "int",
                )
            HookAction.ALLOW_GLASS_WALLPAPER_FILTER -> returnType == "boolean" &&
                parameterTypes.size == 1
            HookAction.SKIP_GLASS_FILTER_DISABLE -> returnType == "void" &&
                parameterTypes.size <= 1
            HookAction.PRESERVE_GLASS_SYSTEMUI -> (returnType == "void" &&
                parameterTypes == listOf(
                    "boolean",
                    "boolean",
                    "com.miui.clock.module.ClockBean",
                )) || (name == "getClockBeanFromSetting" &&
                    returnType == "com.miui.clock.module.ClockBean" &&
                    (parameterTypes.isEmpty() || parameterTypes == listOf("java.lang.String")))
            HookAction.ADAPT_SUPER_WALLPAPER_CLOCK -> when (name) {
                "bindView" -> returnType == "void" &&
                    (parameterTypes.isEmpty() || parameterTypes == listOf("android.view.View"))
                "initClock" -> returnType == "void" && parameterTypes.isEmpty()
                "setSize" -> returnType == "void" && parameterTypes == listOf("int")
                "onLayout" -> returnType == "void" && parameterTypes ==
                    listOf("boolean", "int", "int", "int", "int")
                "updateClockPositionByTime" -> returnType == "void" && parameterTypes == listOf("boolean")
                "update" -> returnType == "void" && parameterTypes.size == 2 &&
                    parameterTypes[1] == "int"
                "initTemplateBean" -> returnType == "void" && parameterTypes.size == 1 &&
                    parameterTypes[0].endsWith(".TemplateConfig")
                "copyExtendedInfoToClockBean" -> returnType == "void" && parameterTypes == listOf(
                    "com.miui.clock.module.ClockBean",
                    "com.miui.keyguard.editor.data.bean.ClockInfo",
                )
                "onMiuiClockViewCreated" -> returnType == "void" && parameterTypes.isEmpty()
                "setData" -> returnType == "void" && parameterTypes.size == 5 &&
                    parameterTypes.take(4).all { it == "float" } && parameterTypes[4] == "int"
                "setBg" -> returnType == "void" && parameterTypes ==
                    listOf("android.view.View", "int")
                "updateAODStyle" -> returnType == "com.miui.aod.widget.IAodClock" &&
                    parameterTypes == listOf("com.miui.aod.common.StyleInfo")
                "updateStyleInfoForPreview" -> returnType == "com.miui.aod.widget.IAodClock" &&
                    parameterTypes.isEmpty()
                "initStyleInfoSelected" -> returnType == "com.miui.aod.widget.IAodClock" &&
                    parameterTypes == listOf("java.lang.String", "java.lang.String", "android.os.Bundle")
                "onColorPickComplete" -> returnType == "void" && parameterTypes.size == 2 &&
                    parameterTypes[0] == "com.miui.keyguard.editor.edit.color.ColorData" &&
                    parameterTypes[1] == "boolean"
                "updateClockColor" -> returnType == "void" && parameterTypes == listOf(
                    "com.miui.keyguard.editor.edit.color.ColorData",
                )
                else -> false
            }
            HookAction.DELEGATE_SUPER_WALLPAPER_SLIDE -> returnType == "void" &&
                parameterTypes == listOf("android.view.View", "float")
            HookAction.PRESERVE_SUPER_WALLPAPER_EDITOR -> when (name) {
                "isInValid", "supportSuperWallpaperMode", "isSupportDepth", "isSupportHierarchy" ->
                    returnType == "boolean" && parameterTypes.isEmpty()
                "supportFilter" -> returnType == "boolean" && parameterTypes.isEmpty()
                "getStyleInfo", "getClockStyleInfo" -> returnType == "com.miui.aod.common.StyleInfo" &&
                    parameterTypes == listOf("android.content.Context")
                else -> false
            }
            HookAction.PRESERVE_SUPER_WALLPAPER_DEPTH -> when (name) {
                "isWallpaperSupportDepth" -> returnType == "boolean" && parameterTypes.isEmpty()
                "setWallpaperSupportDepth" -> returnType == "void" &&
                    parameterTypes == listOf("boolean")
                else -> false
            }
        }
    }
}

internal data class ClassHookProfile(
    val className: String,
    val rules: List<MethodHookRule>,
)

/**
 * HyperOS 3 has used both AOSP-like and MIUI package paths across device branches.
 * Keep the fallback class names here, while method installation remains signature-checked.
 */
internal object HookProfiles {
    private val freeHorizontalFriction = MethodHookRule(
        names = setOf("applyFriction"),
        action = HookAction.FREE_HORIZONTAL_FRICTION,
    )

    private val preserveHorizontalDragSession = MethodHookRule(
        names = setOf("adjustBoundsAndScalePostUpdate", "adjustFreeformBoundsAndScale"),
        action = HookAction.PRESERVE_HORIZONTAL_DRAG_SESSION,
    )

    private val preserveHorizontalStableOffset = MethodHookRule(
        names = setOf("offsetBoundsByStableBounds"),
        action = HookAction.PRESERVE_HORIZONTAL_STABLE_OFFSET,
    )

    private val preserveHorizontalAnimTargetParam = MethodHookRule(
        names = setOf("setBaseAnimTargetParam"),
        action = HookAction.PRESERVE_HORIZONTAL_ANIM_TARGET_PARAM,
    )

    private val preserveHorizontalMoveFinalBounds = MethodHookRule(
        names = setOf("getFinalBounds"),
        action = HookAction.PRESERVE_HORIZONTAL_MOVE_FINAL_BOUNDS,
    )

    private val allowGlassOnAnyWallpaper = MethodHookRule(
        names = setOf("glassEffectDisable"),
        action = HookAction.ALLOW_GLASS_ON_ANY_WALLPAPER,
    )

    private val preserveGlassEffect = MethodHookRule(
        names = setOf("computeSupportedClockEffect"),
        action = HookAction.PRESERVE_GLASS_EFFECT,
    )

    private val allowGlassWallpaperFilter = MethodHookRule(
        names = setOf("isWallpaperSupportGlassFilter"),
        action = HookAction.ALLOW_GLASS_WALLPAPER_FILTER,
    )

    private val skipGlassFilterDisable = MethodHookRule(
        names = setOf("disableGlassFilter"),
        action = HookAction.SKIP_GLASS_FILTER_DISABLE,
    )

    private val preserveGlassSystemUi = MethodHookRule(
        names = setOf("setClockBean", "getClockBeanFromSetting"),
        action = HookAction.PRESERVE_GLASS_SYSTEMUI,
    )

    private val adaptSuperWallpaperClock = MethodHookRule(
        names = setOf(
            "bindView", "setSize", "onLayout", "updateClockPositionByTime", "update",
            "initClock", "initTemplateBean", "copyExtendedInfoToClockBean",
            "onMiuiClockViewCreated", "setData", "setBg",
            "updateAODStyle", "updateStyleInfoForPreview", "initStyleInfoSelected", "onColorPickComplete",
            "updateClockColor",
        ),
        action = HookAction.ADAPT_SUPER_WALLPAPER_CLOCK,
    )

    private val delegateSuperWallpaperSlide = MethodHookRule(
        names = setOf("onSlide"),
        action = HookAction.DELEGATE_SUPER_WALLPAPER_SLIDE,
    )

    private val preserveSuperWallpaperEditor = MethodHookRule(
        names = setOf(
            "isInValid", "supportSuperWallpaperMode", "isSupportDepth", "isSupportHierarchy", "supportFilter",
            "getStyleInfo", "getClockStyleInfo",
        ),
        action = HookAction.PRESERVE_SUPER_WALLPAPER_EDITOR,
    )

    private val preserveSuperWallpaperDepth = MethodHookRule(
        names = setOf("isWallpaperSupportDepth", "setWallpaperSupportDepth"),
        action = HookAction.PRESERVE_SUPER_WALLPAPER_DEPTH,
    )

    val systemServer = emptyList<ClassHookProfile>()

    val systemUi = listOf(
        ClassHookProfile(
            "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeController",
            listOf(preserveHorizontalDragSession),
        ),
        ClassHookProfile(
            "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeUtils",
            listOf(freeHorizontalFriction, preserveHorizontalStableOffset),
        ),
        ClassHookProfile(
            "com.android.wm.shell.multitasking.common.animation.MultiTaskingAnimTarget",
            listOf(preserveHorizontalAnimTargetParam),
        ),
        ClassHookProfile(
            "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeMoveHandler",
            listOf(preserveHorizontalMoveFinalBounds),
        ),
        ClassHookProfile(
            "com.miui.clock.MiuiClockController",
            listOf(preserveGlassSystemUi, preserveSuperWallpaperDepth),
        ),
    )

    val aod = listOf(
        ClassHookProfile(
            "com.miui.keyguard.editor.viewmodel.EditFragmentViewModel\$Companion",
            listOf(allowGlassOnAnyWallpaper, preserveGlassEffect),
        ),
        ClassHookProfile(
            "com.miui.keyguard.editor.edit.base.EffectsTemplateView",
            listOf(allowGlassWallpaperFilter, skipGlassFilterDisable, preserveSuperWallpaperEditor),
        ),
        ClassHookProfile(
            "com.miui.aod.components.view.SuperWallpaperStyleSelectView",
            listOf(delegateSuperWallpaperSlide),
        ),
        ClassHookProfile(
            "com.miui.aod.components.view.BaseStyleSelectView",
            listOf(adaptSuperWallpaperClock),
        ),
        ClassHookProfile(
            "com.miui.aod.AODStyleController",
            listOf(preserveSuperWallpaperEditor),
        ),
        ClassHookProfile(
            "com.miui.aod.widget.AODSettings",
            listOf(preserveSuperWallpaperEditor),
        ),
        ClassHookProfile(
            "com.miui.aod.category.SuperWallpaperCategoryInfo",
            listOf(adaptSuperWallpaperClock, preserveSuperWallpaperEditor),
        ),
        ClassHookProfile(
            "com.miui.aod.category.BlankSuperWallpaperCategoryInfo",
            listOf(adaptSuperWallpaperClock, preserveSuperWallpaperEditor),
        ),
        ClassHookProfile(
            "com.miui.aod.widget.SuperWallpaperClock",
            listOf(adaptSuperWallpaperClock),
        ),
        ClassHookProfile(
            "com.miui.aod.widget.SuperWallpaperClockView",
            listOf(adaptSuperWallpaperClock),
        ),
        ClassHookProfile(
            "com.miui.aod.template.data.template.base.BaseTemplateConfig",
            listOf(adaptSuperWallpaperClock),
        ),
        ClassHookProfile(
            "com.miui.aod.template.data.template.classicclock.AllInOneTemplateConfig",
            listOf(adaptSuperWallpaperClock),
        ),
        ClassHookProfile(
            "com.miui.aod.common.StyleInfo",
            listOf(adaptSuperWallpaperClock, preserveSuperWallpaperEditor),
        ),
    )
}
