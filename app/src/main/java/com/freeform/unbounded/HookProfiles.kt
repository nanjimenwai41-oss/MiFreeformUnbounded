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
    /** Returns a verified renderer depth frame to the SystemUI consumer. */
    PROVIDE_SUPER_WALLPAPER_DEPTH_FRAME,
    /** Establishes or invalidates the local Super-wallpaper compatibility session. */
    ESTABLISH_SUPER_WALLPAPER_SESSION,
    /** Prepares inputs consumed by the native dynamic-video depth evaluator. */
    PREPARE_VIDEO_DEPTH_INPUT,
    /** Observes the real SystemUI video-depth state machine without changing results. */
    OBSERVE_VIDEO_DEPTH_CHAIN,
    /** 监听当前 OS4 DEX 已核实的锁屏/AOD 联动动画入口。 */
    OBSERVE_AOD_CLOCK_TRANSITION,
    /** Observes depth-frame production in MiWallpaper/FastPlayer. */
    OBSERVE_VIDEO_DEPTH_SOURCE,
    /** Observes the verified VideoDepthManager -> FastPlayer render path. */
    OBSERVE_VIDEO_DEPTH_RENDER_PATH,
    /** Observes the Binder callback that delivers the last depth frame to consumers. */
    OBSERVE_VIDEO_DEPTH_CALLBACK,
    /** Captures the callback object registered by a video-depth engine. */
    CAPTURE_VIDEO_DEPTH_CALLBACK,
    /** Supplies a generated Super-wallpaper depth frame to the real engine. */
    ADAPT_VIDEO_DEPTH_SOURCE,
    /** Observes concrete Super-wallpaper Engine/Renderer depth inputs. */
    OBSERVE_SUPER_WALLPAPER_ENGINE,
    /** Observes the concrete Snowmountain Java/native render runtime. */
    OBSERVE_SUPER_WALLPAPER_RUNTIME,
    /** Observes the verified WallpaperServiceController listener registration. */
    OBSERVE_WALLPAPER_REGISTRATION,
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
        if (action != HookAction.CAPTURE_VIDEO_DEPTH_CALLBACK &&
            action != HookAction.OBSERVE_SUPER_WALLPAPER_ENGINE &&
            action != HookAction.OBSERVE_SUPER_WALLPAPER_RUNTIME &&
            name !in names
        ) return false
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
                (parameterTypes == listOf(
                    "com.miui.keyguard.editor.data.bean.CommonConfig",
                    "int",
                ) || parameterTypes == listOf(
                    "com.miui.keyguard.editor.data.bean.CommonConfig",
                    "int",
                    "android.content.Context",
                ))
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
                "updateShowDepthState" -> returnType == "void" && parameterTypes.isEmpty()
                "getDepthEffectEnable" -> returnType == "boolean" && parameterTypes.isEmpty()
                "handleValueChanged" -> returnType == "void" && parameterTypes == listOf("int", "boolean")
                "updateDeductedImageView" -> returnType == "void" && parameterTypes.isEmpty()
                else -> false
            }
            HookAction.ENABLE_SUPER_WALLPAPER_VIDEO_RENDER ->
                name == "isDepthVideoEnable" && returnType == "boolean" && parameterTypes.isEmpty()
            HookAction.PROVIDE_SUPER_WALLPAPER_DEPTH_FRAME ->
                name == "getLastDepthFrameBitmap" && (
                    // HyperOS 3 on the target device uses the real MIUI
                    // contract: the method is void and delivers its Bitmap
                    // through java.util.function.Function.
                    (returnType == "void" && parameterTypes == listOf("java.util.function.Function")) ||
                        // Keep the older synchronous shape for other MIUI
                        // branches; the interceptor handles it pass-through.
                        (returnType == "android.graphics.Bitmap" &&
                            (parameterTypes.isEmpty() || parameterTypes == listOf("int")))
                    )
            HookAction.ESTABLISH_SUPER_WALLPAPER_SESSION -> returnType == "void" || returnType == "boolean" || returnType.endsWith("WallpaperInfo")
            HookAction.PREPARE_VIDEO_DEPTH_INPUT -> returnType == "void" || returnType == "boolean"
            HookAction.OBSERVE_VIDEO_DEPTH_CHAIN -> when (name) {
                "initDepthBitmapAvoid" -> returnType == "void" && parameterTypes == listOf(
                    "android.graphics.Bitmap", "android.net.Uri",
                )
                "updateVideoDepthVisibility" -> returnType == "void" && parameterTypes == listOf("int", "boolean", "boolean")
                else -> returnType == "void" && parameterTypes.isEmpty()
            }
            HookAction.OBSERVE_AOD_CLOCK_TRANSITION -> name == "linkageViewAnim\$default" &&
                returnType == "void" && parameterTypes == listOf(
                    "com.android.keyguard.panel.KeyguardPanelViewController", "boolean", "java.lang.String", "int",
                )
            HookAction.OBSERVE_VIDEO_DEPTH_SOURCE -> name in setOf("getLastDepthFrame", "getDepthFrameAtTime") &&
                returnType == "android.graphics.Bitmap" && when (name) {
                    "getLastDepthFrame" -> parameterTypes == listOf("java.lang.String")
                    "getDepthFrameAtTime" -> parameterTypes == listOf("java.lang.String", "long")
                    else -> false
                }
            HookAction.OBSERVE_VIDEO_DEPTH_RENDER_PATH -> when (name) {
                "i" -> returnType == "void" && parameterTypes == listOf(
                    "android.view.Surface", "android.view.Surface",
                )
                "e" -> returnType == "void" && parameterTypes.isEmpty()
                "f" -> returnType == "void" && parameterTypes == listOf("boolean")
                "controlVideoDepth" -> returnType == "void" && parameterTypes == listOf("int")
                "setLockscreenVideoSurface" -> returnType == "void" && parameterTypes == listOf(
                    "android.view.Surface", "android.view.Surface",
                )
                "updateVideoWallpaperFrame" -> returnType == "void" && parameterTypes == listOf("int")
                "setSurface" -> returnType == "void" && parameterTypes == listOf(
                    "android.view.Surface", "android.view.Surface", "android.view.Surface",
                    "int", "android.graphics.Bitmap",
                )
                "addDataSource" -> returnType == "void" && parameterTypes == listOf(
                    "android.content.Context", "android.net.Uri", "int",
                )
                "changeOpenGLSurface" -> returnType == "int" && parameterTypes == listOf(
                    "android.view.Surface", "boolean", "android.view.Surface", "boolean",
                    "android.view.Surface", "boolean",
                )
                "setOpenGLSurfaceVisible" -> returnType == "void" && parameterTypes == listOf(
                    "boolean", "boolean", "boolean",
                )
                else -> false
            }
            HookAction.OBSERVE_VIDEO_DEPTH_CALLBACK -> when (name) {
                "onGetLastDepthFrameSuccess" -> returnType == "void" &&
                    parameterTypes == listOf("android.graphics.Bitmap", "int")
                "onGetLastDepthFrameFailed" -> returnType == "void" &&
                    parameterTypes == listOf("int", "java.lang.String")
                else -> false
            }
            HookAction.CAPTURE_VIDEO_DEPTH_CALLBACK -> parameterTypes.any { type ->
                type == "com.miui.miwallpaper.IMiuiVideoDepthLastFrameCallback" ||
                    type.endsWith(".IMiuiVideoDepthLastFrameCallback")
            }
            HookAction.ADAPT_VIDEO_DEPTH_SOURCE ->
                name in setOf("e", "getLastDepthFrameBitmap") &&
                    returnType == "android.graphics.Bitmap" && parameterTypes.isEmpty()
            HookAction.OBSERVE_SUPER_WALLPAPER_ENGINE -> {
                val depthName = name.contains("depth", ignoreCase = true) ||
                    name.contains("texture", ignoreCase = true) ||
                    name.contains("filament", ignoreCase = true) ||
                    name in setOf(
                        "onDrawFrame", "drawFrame", "render", "renderFrame", "updateFrame",
                        "setFrame", "setTexture", "loadTexture", "bindTexture",
                        "onSurfaceCreated", "onSurfaceDestroyed", "onSurfaceTextureUpdated",
                        "sendFilamentMessage",
                    )
                depthName && (returnType == "void" ||
                    returnType == "android.graphics.Bitmap" ||
                    returnType == "java.nio.ByteBuffer" ||
                    returnType == "android.view.Surface" ||
                    returnType == "android.graphics.SurfaceTexture" ||
                    parameterTypes.any { type ->
                        type == "android.graphics.Bitmap" ||
                            type == "java.nio.ByteBuffer" ||
                            type == "android.view.Surface" ||
                            type == "android.graphics.SurfaceTexture"
                    })
            }
            HookAction.OBSERVE_SUPER_WALLPAPER_RUNTIME -> when (name) {
                "a", "d" -> returnType == "void" && parameterTypes.isEmpty()
                "b", "c", "nSendMessage" -> returnType == "void" &&
                    parameterTypes == listOf("java.lang.String")
                "doFrame" -> returnType == "void" && parameterTypes == listOf("long")
                "threadSwapBuffers" -> returnType == "void" && parameterTypes == listOf("int")
                "nDestroy", "nPause", "nResume" -> returnType == "void" && parameterTypes.isEmpty()
                "nGetActualTargetFrameRate" -> returnType == "int" && parameterTypes.isEmpty()
                "nInit" -> returnType == "void" && parameterTypes == listOf(
                    "int",
                    "int",
                    "java.lang.String",
                    "java.lang.String",
                    "java.lang.String",
                    "android.content.res.AssetManager",
                )
                "nRender" -> returnType == "void" && parameterTypes == listOf("long")
                "nResizeViewPort" -> returnType == "void" && parameterTypes == listOf("int", "int")
                "nSetSurface" -> returnType == "void" && parameterTypes == listOf("android.view.Surface")
                else -> false
            }
            HookAction.OBSERVE_WALLPAPER_REGISTRATION -> when (name) {
                "onCreate" -> returnType == "void" && parameterTypes == listOf("android.view.SurfaceHolder")
                "onDestroy" -> returnType == "void" && parameterTypes.isEmpty()
                "setLockscreenVideoSurface" -> returnType == "void" &&
                    parameterTypes == listOf("android.view.Surface", "android.view.Surface")
                // WallpaperServiceController stores active engines in a
                // ConcurrentHashMap. The map methods are observed only after
                // the hook verifies the receiver is the controller's field r.
                "put" -> returnType == "java.lang.Object" && parameterTypes == listOf(
                    "java.lang.Object",
                    "java.lang.Object",
                )
                "remove" ->
                    (returnType == "java.lang.Object" || returnType == "boolean") &&
                        parameterTypes == listOf("java.lang.Object")
                "clear" -> returnType == "void" && parameterTypes.isEmpty()
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
        names = setOf("glassEffectDisable", "shouldDisableGlassEffect"),
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
        names = setOf(
            "isWallpaperSupportDepth",
            "setWallpaperSupportDepth",
            "getDepthAvoidRect",
            "updateShowDepthState",
            "getDepthEffectEnable",
            "handleValueChanged",
            "updateDeductedImageView",
        ),
        action = HookAction.ENABLE_SUPER_WALLPAPER_VIDEO_DEPTH,
    )

    private val enableSuperWallpaperVideoRender = MethodHookRule(
        names = setOf("isDepthVideoEnable"),
        action = HookAction.ENABLE_SUPER_WALLPAPER_VIDEO_RENDER,
    )

    private val provideSuperWallpaperDepthFrame = MethodHookRule(
        names = setOf("getLastDepthFrameBitmap"),
        action = HookAction.PROVIDE_SUPER_WALLPAPER_DEPTH_FRAME,
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
            "updateKeyguardElementsVisibility",
        ),
        action = HookAction.OBSERVE_VIDEO_DEPTH_CHAIN,
    )

    private val observeAodClockTransition = MethodHookRule(
        names = setOf("linkageViewAnim\$default"),
        action = HookAction.OBSERVE_AOD_CLOCK_TRANSITION,
    )

    private val observeVideoDepthSource = MethodHookRule(
        names = setOf("getLastDepthFrame", "getDepthFrameAtTime"),
        action = HookAction.OBSERVE_VIDEO_DEPTH_SOURCE,
    )

    private val observeVideoDepthRenderPath = MethodHookRule(
        names = setOf(
            "i", "e", "f", "controlVideoDepth", "setLockscreenVideoSurface",
            "updateVideoWallpaperFrame", "setSurface", "addDataSource",
            "changeOpenGLSurface", "setOpenGLSurfaceVisible",
        ),
        action = HookAction.OBSERVE_VIDEO_DEPTH_RENDER_PATH,
    )

    private val observeVideoDepthCallback = MethodHookRule(
        names = setOf("onGetLastDepthFrameSuccess", "onGetLastDepthFrameFailed"),
        action = HookAction.OBSERVE_VIDEO_DEPTH_CALLBACK,
    )

    private val captureVideoDepthCallback = MethodHookRule(
        names = emptySet(),
        action = HookAction.CAPTURE_VIDEO_DEPTH_CALLBACK,
    )

    private val adaptVideoDepthSource = MethodHookRule(
        names = setOf("e", "getLastDepthFrameBitmap"),
        action = HookAction.ADAPT_VIDEO_DEPTH_SOURCE,
    )

    private val observeSuperWallpaperEngine = MethodHookRule(
        names = setOf(
            "getDepthBitmap", "getDepthFrame", "getDepthTexture", "setDepthTexture",
            "updateDepth", "updateDepthTexture", "loadDepth", "loadDepthTexture",
            "renderDepth", "uploadDepth", "sendFilamentMessage",
        ),
        action = HookAction.OBSERVE_SUPER_WALLPAPER_ENGINE,
    )

    private val observeSuperWallpaperRuntime = MethodHookRule(
            names = setOf(
            "a", "b", "c", "d", "doFrame", "threadSwapBuffers",
            "nDestroy", "nGetActualTargetFrameRate", "nInit", "nPause", "nRender",
            "nResizeViewPort", "nResume", "nSendMessage", "nSetSurface",
        ),
        action = HookAction.OBSERVE_SUPER_WALLPAPER_RUNTIME,
    )

    private val observeWallpaperRegistration = MethodHookRule(
        names = setOf(
            "onCreate",
            "onDestroy",
            "setLockscreenVideoSurface",
            "put",
            "remove",
            "clear",
        ),
        action = HookAction.OBSERVE_WALLPAPER_REGISTRATION,
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
            listOf(
                enableSuperWallpaperVideoRender,
                provideSuperWallpaperDepthFrame,
                establishSuperWallpaperSession,
                prepareVideoDepthInput,
            ),
        ),
        ClassHookProfile(
            "com.android.keyguard.depth.KeyguardDepthInteractor",
            // updateDeductedImageView is present in both profiles. The depth
            // state handler must win so a verified Bitmap can bypass the
            // stock crop_subject-file-only coroutine.
            listOf(enableSuperWallpaperVideoDepth, observeVideoDepthChain),
        ),
        ClassHookProfile(
            "com.android.keyguard.panel.KeyguardPanelViewController",
            // 同时监听 DEX 已核实的静态 AOD 联动入口与锁屏可见性。
            listOf(enableSuperWallpaperVideoDepth, observeVideoDepthChain, observeAodClockTransition),
        ),
        ClassHookProfile(
            "com.android.keyguard.panel.KeyguardPanelViewController\$onViewAttachedToWindow\$1",
            listOf(enableSuperWallpaperVideoDepth),
        ),
        ClassHookProfile(
            "com.miui.keyguard.KeyguardCommonSettingObserver",
            listOf(enableSuperWallpaperVideoDepth),
        ),
        ClassHookProfile(
            "com.miui.keyguard.VideoDepthSurfaceHolder",
            listOf(observeVideoDepthChain),
        ),
    )

    val miWallpaper = listOf(
        ClassHookProfile(
            "java.util.concurrent.ConcurrentHashMap",
            listOf(observeWallpaperRegistration),
        ),
        ClassHookProfile(
            "java.util.concurrent.ConcurrentHashMap\$ValuesView",
            listOf(observeWallpaperRegistration),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.manager.WallpaperServiceController",
            listOf(observeWallpaperRegistration),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.wallpaperservice.UniversalWallpaper\$UniversalEngine",
            listOf(observeWallpaperRegistration),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.wallpaperservice.UniversalWallpaper\$UniversalEngine\$2",
            listOf(observeWallpaperRegistration),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.server.MiuiWallPaperControllerService\$MiuiWallpaperRemoteService",
            listOf(observeWallpaperRegistration),
        ),
        ClassHookProfile(
            "com.miui.fastplayer.FastPlayer",
            listOf(observeVideoDepthSource, observeVideoDepthRenderPath),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.container.videodepth.VideoDepthManager",
            listOf(observeVideoDepthSource, observeVideoDepthRenderPath, captureVideoDepthCallback),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.container.videodepth.VideoDepthEngineImpl",
            listOf(observeVideoDepthSource, observeVideoDepthRenderPath, adaptVideoDepthSource, captureVideoDepthCallback),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.wallpaperservice.impl.VideoDepthEngineImpl",
            listOf(observeVideoDepthSource, observeVideoDepthRenderPath, adaptVideoDepthSource, captureVideoDepthCallback),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.wallpaperservice.impl.keyguard.KeyguardVideoDepthEngineImpl",
            listOf(observeVideoDepthSource, observeVideoDepthRenderPath, adaptVideoDepthSource, captureVideoDepthCallback),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.IMiuiVideoDepthLastFrameCallback",
            listOf(observeVideoDepthCallback),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.IMiuiVideoDepthLastFrameCallback\$Stub",
            listOf(observeVideoDepthCallback),
        ),
        // These are the concrete Snowmountain renderer classes in the current
        // device build. Keep them explicit because the obfuscated methods do
        // not satisfy the generic depth/texture discovery predicate.
        ClassHookProfile("n2.a", listOf(observeSuperWallpaperRuntime)),
        ClassHookProfile("n2.a\$a", listOf(observeSuperWallpaperRuntime)),
        ClassHookProfile("com.xiaomi.utils.JNIUtils", listOf(observeSuperWallpaperRuntime)),
        ClassHookProfile(
            "com.miui.miwallpaper.thread.bhook.S1ThreadHooker",
            listOf(observeSuperWallpaperRuntime),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.moon.superwallpaper.MoonSuperWallpaper",
            listOf(observeSuperWallpaperEngine),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.snowmountain.superwallpaper.SnowmountainSuperWallpaper",
            listOf(observeSuperWallpaperEngine),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.geometry.superwallpaper.GeometrySuperWallpaper",
            listOf(observeSuperWallpaperEngine),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.saturn.superwallpaper.SaturnSuperWallpaper",
            listOf(observeSuperWallpaperEngine),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.earth.superwallpaper.EarthSuperWallpaper",
            listOf(observeSuperWallpaperEngine),
        ),
        ClassHookProfile(
            "com.miui.miwallpaper.mars.superwallpaper.MarsSuperWallpaper",
            listOf(observeSuperWallpaperEngine),
        ),
    )

    fun superWallpaperEngine(className: String): ClassHookProfile = ClassHookProfile(
        className,
        listOf(observeSuperWallpaperEngine),
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
