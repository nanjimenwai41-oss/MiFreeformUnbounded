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
    /** Reuses the dynamic-video depth path for a selected Super wallpaper. */
    ENABLE_SUPER_WALLPAPER_VIDEO_DEPTH,
    /** Allows the keyguard video-depth surface path for a selected Super wallpaper. */
    ENABLE_SUPER_WALLPAPER_VIDEO_RENDER,
    /** Establishes or invalidates the local Super-wallpaper compatibility session. */
    ESTABLISH_SUPER_WALLPAPER_SESSION,
    /** Prepares inputs consumed by the native dynamic-video depth evaluator. */
    PREPARE_VIDEO_DEPTH_INPUT,
    /** Observes the real SystemUI video-depth state machine without changing results. */
    OBSERVE_VIDEO_DEPTH_CHAIN,
    /** Observes depth-frame production in MiWallpaper/FastPlayer. */
    OBSERVE_VIDEO_DEPTH_SOURCE,
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
            HookAction.ENABLE_SUPER_WALLPAPER_VIDEO_DEPTH -> when (name) {
                "isWallpaperSupportDepth" -> returnType == "boolean" && parameterTypes.isEmpty()
                "setWallpaperSupportDepth" -> returnType == "void" &&
                    parameterTypes == listOf("boolean")
                "getDepthAvoidRect" -> returnType == Rect::class.java.name && parameterTypes.isEmpty()
                else -> false
            }
            HookAction.ENABLE_SUPER_WALLPAPER_VIDEO_RENDER ->
                name == "isDepthVideoEnable" && returnType == "boolean" && parameterTypes.isEmpty()
            HookAction.ESTABLISH_SUPER_WALLPAPER_SESSION -> returnType == "void" || returnType == "boolean" || returnType.endsWith("WallpaperInfo")
            HookAction.PREPARE_VIDEO_DEPTH_INPUT -> returnType == "void" || returnType == "boolean"
            HookAction.OBSERVE_VIDEO_DEPTH_CHAIN -> when (name) {
                "initDepthBitmapAvoid" -> returnType == "void" && parameterTypes == listOf(
                    "android.graphics.Bitmap", "android.net.Uri",
                )
                "updateVideoDepthVisibility" -> returnType == "void" && parameterTypes == listOf("int", "boolean", "boolean")
                else -> returnType == "void" && parameterTypes.isEmpty()
            }
            HookAction.OBSERVE_VIDEO_DEPTH_SOURCE -> name == "getLastDepthFrame" &&
                returnType == "android.graphics.Bitmap" && parameterTypes == listOf("java.lang.String")
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

    private val enableSuperWallpaperVideoDepth = MethodHookRule(
        names = setOf("isWallpaperSupportDepth", "setWallpaperSupportDepth", "getDepthAvoidRect"),
        action = HookAction.ENABLE_SUPER_WALLPAPER_VIDEO_DEPTH,
    )

    private val enableSuperWallpaperVideoRender = MethodHookRule(
        names = setOf("isDepthVideoEnable"),
        action = HookAction.ENABLE_SUPER_WALLPAPER_VIDEO_RENDER,
    )

    private val establishSuperWallpaperSession = MethodHookRule(
        names = setOf("setWallpaperInfo", "updateWallpaperInfo", "onWallpaperChanged", "onWallpaperChangedComplete", "bindWallpaper", "rebindWallpaper", "setKeyguardWallpaper", "onClockViewCreated", "onConfigurationChanged", "onDisplayChanged", "onSurfaceCreated", "onSurfaceDestroyed"),
        action = HookAction.ESTABLISH_SUPER_WALLPAPER_SESSION,
    )

    private val prepareVideoDepthInput = MethodHookRule(
        names = setOf("initWallpaperDepth", "initializeWallpaperDepth", "prepareDepthAvoidRule", "prepareVideoDepth", "setDepthVideoConfig", "updateDepthAvoidRule"),
        action = HookAction.PREPARE_VIDEO_DEPTH_INPUT,
    )

    private val observeVideoDepthChain = MethodHookRule(
        names = setOf(
            "initDepthBitmapAvoid",
            "removeVideoDepthSurface",
            "updateDeductedImageView",
            "updateVideoDepthSurface",
            "updateVideoDepthVisibility",
        ),
        action = HookAction.OBSERVE_VIDEO_DEPTH_CHAIN,
    )

    private val observeVideoDepthSource = MethodHookRule(
        names = setOf("getLastDepthFrame"),
        action = HookAction.OBSERVE_VIDEO_DEPTH_SOURCE,
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
            listOf(
                preserveGlassSystemUi,
                enableSuperWallpaperVideoDepth,
                establishSuperWallpaperSession,
                prepareVideoDepthInput,
            ),
        ),
        ClassHookProfile(
            "com.android.keyguard.wallpaper.MiuiKeyguardWallPaperManager",
            listOf(enableSuperWallpaperVideoRender, establishSuperWallpaperSession, prepareVideoDepthInput),
        ),
        ClassHookProfile(
            "com.android.keyguard.depth.KeyguardDepthInteractor",
            listOf(observeVideoDepthChain),
        ),
        ClassHookProfile(
            "com.miui.keyguard.VideoDepthSurfaceHolder",
            listOf(observeVideoDepthChain),
        ),
    )

    val miWallpaper = listOf(
        ClassHookProfile(
            "com.miui.fastplayer.FastPlayer",
            listOf(observeVideoDepthSource),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.container.videodepth.VideoDepthManager",
            listOf(observeVideoDepthSource),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.container.videodepth.VideoDepthEngineImpl",
            listOf(observeVideoDepthSource),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.wallpaperservice.impl.VideoDepthEngineImpl",
            listOf(observeVideoDepthSource),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.wallpaperservice.impl.keyguard.KeyguardVideoDepthEngineImpl",
            listOf(observeVideoDepthSource),
        ),
    )

    val aod = listOf(
        ClassHookProfile(
            "com.miui.keyguard.editor.viewmodel.EditFragmentViewModel\$Companion",
            listOf(allowGlassOnAnyWallpaper, preserveGlassEffect),
        ),
        ClassHookProfile(
            "com.miui.keyguard.editor.edit.base.EffectsTemplateView",
            listOf(allowGlassWallpaperFilter, skipGlassFilterDisable),
        ),
    )
}
