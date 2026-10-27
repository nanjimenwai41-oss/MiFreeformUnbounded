package com.freeform.unbounded

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookProfilesTest {
    @Test
    fun booleanDisableRuleRequiresExactNameAndBooleanReturnType() {
        val rule = MethodHookRule(setOf("isEnterPin"), HookAction.DISABLE_BOOLEAN)

        assertTrue(rule.matches("isEnterPin", "boolean", emptyList()))
        assertFalse(rule.matches("isEnterPin", "java.lang.Boolean", emptyList()))
        assertFalse(rule.matches("isEnterPinAnimation", "boolean", emptyList()))
    }

    @Test
    fun horizontalFrictionRuleOnlyMatchesFreeformUtilityOverload() {
        val rule = MethodHookRule(setOf("applyFriction"), HookAction.FREE_HORIZONTAL_FRICTION)

        assertTrue(
            rule.matches(
                "applyFriction",
                "android.graphics.Rect",
                listOf(
                    "android.content.Context",
                    "android.graphics.Rect",
                    "android.graphics.Rect",
                    "float",
                    "android.graphics.PointF",
                    "boolean",
                ),
            )
        )
        assertFalse(
            rule.matches(
                "applyFriction",
                "android.graphics.PointF",
                listOf("android.graphics.Rect", "android.graphics.PointF"),
            )
        )
    }

    @Test
    fun dragSessionRuleMatchesPostUpdateAndAdjustHookPoints() {
        val rule = MethodHookRule(
            setOf("adjustBoundsAndScalePostUpdate", "adjustFreeformBoundsAndScale"),
            HookAction.PRESERVE_HORIZONTAL_DRAG_SESSION,
        )

        assertTrue(
            rule.matches(
                "adjustBoundsAndScalePostUpdate",
                "void",
                listOf(
                    "android.window.WindowContainerTransaction",
                    "android.graphics.Rect",
                    "com.android.wm.shell.multitasking.common.taskmanager.MiuiFreeformModeTaskInfo",
                ),
            )
        )
        assertTrue(
            rule.matches(
                "adjustFreeformBoundsAndScale",
                "void",
                listOf(
                    "com.android.wm.shell.multitasking.common.taskmanager.MiuiFreeformModeTaskInfo",
                    "android.graphics.Rect",
                    "android.graphics.Rect",
                    "float",
                ),
            )
        )
        assertFalse(
            rule.matches(
                "adjustBoundsAndScalePostUpdate",
                "void",
                listOf("android.graphics.Rect"),
            )
        )
    }

    @Test
    fun stableOffsetRuleOnlyMatchesVoidRectRectFloatOverload() {
        val rule = MethodHookRule(setOf("offsetBoundsByStableBounds"), HookAction.PRESERVE_HORIZONTAL_STABLE_OFFSET)

        assertTrue(
            rule.matches(
                "offsetBoundsByStableBounds",
                "void",
                listOf("android.graphics.Rect", "android.graphics.Rect", "float"),
            )
        )
        assertFalse(
            rule.matches(
                "offsetBoundsByStableBounds",
                "android.graphics.Rect",
                listOf("android.graphics.Rect", "android.graphics.Rect", "float"),
            )
        )
    }

    @Test
    fun animationTargetRuleOnlyMatchesBaseParamSetter() {
        val rule = MethodHookRule(setOf("setBaseAnimTargetParam"), HookAction.PRESERVE_HORIZONTAL_ANIM_TARGET_PARAM)

        assertTrue(
            rule.matches(
                "setBaseAnimTargetParam",
                "void",
                listOf("android.graphics.Rect", "float", "float", "float"),
            )
        )
        assertFalse(
            rule.matches(
                "setAnimParam",
                "void",
                listOf("android.graphics.Rect", "float", "float", "float"),
            )
        )
    }

    @Test
    fun moveFinalBoundsRuleOnlyMatchesMoveHandlerFinalBounds() {
        val rule = MethodHookRule(setOf("getFinalBounds"), HookAction.PRESERVE_HORIZONTAL_MOVE_FINAL_BOUNDS)

        assertTrue(
            rule.matches(
                "getFinalBounds",
                "android.graphics.Rect",
                listOf(
                    "com.android.wm.shell.multitasking.common.taskmanager.MiuiFreeformModeTaskInfo",
                    "int",
                    "float",
                    "float",
                    "float",
                    "float",
                    "android.graphics.PointF",
                    "float",
                ),
            )
        )
        assertFalse(
            rule.matches(
                "getFinalBounds",
                "android.graphics.Rect",
                listOf("android.graphics.Rect", "float"),
            )
        )
    }

    @Test
    fun everyConfiguredUiProfileHasRules() {
        assertTrue(HookProfiles.systemUi.all { it.rules.isNotEmpty() })
    }

    @Test
    fun fastPlayerDepthFrameRulesMatchBothRealProducerMethods() {
        val rule = HookProfiles.miWallpaper
            .flatMap { it.rules }
            .first { it.action == HookAction.OBSERVE_VIDEO_DEPTH_SOURCE }

        assertTrue(rule.matches("getLastDepthFrame", "android.graphics.Bitmap", listOf("java.lang.String")))
        assertTrue(rule.matches("getDepthFrameAtTime", "android.graphics.Bitmap", listOf("java.lang.String", "long")))
        assertFalse(rule.matches("getDepthFrameAtTime", "android.graphics.Bitmap", listOf("java.lang.String", "int")))
    }

    @Test
    fun videoDepthRenderPathRulesMatchOnlyVerifiedCurrentDeviceSignatures() {
        val rule = HookProfiles.miWallpaper
            .flatMap { it.rules }
            .first { it.action == HookAction.OBSERVE_VIDEO_DEPTH_RENDER_PATH }

        assertTrue(rule.matches("i", "void", listOf("android.view.Surface", "android.view.Surface")))
        assertTrue(rule.matches("setSurface", "void", listOf(
            "android.view.Surface", "android.view.Surface", "android.view.Surface",
            "int", "android.graphics.Bitmap",
        )))
        assertTrue(rule.matches("addDataSource", "void", listOf(
            "android.content.Context", "android.net.Uri", "int",
        )))
        assertTrue(rule.matches("changeOpenGLSurface", "int", listOf(
            "android.view.Surface", "boolean", "android.view.Surface", "boolean",
            "android.view.Surface", "boolean",
        )))
        assertFalse(rule.matches("i", "void", listOf("android.view.Surface")))
        assertFalse(rule.matches("setSurface", "void", listOf("android.view.Surface")))
        assertFalse(rule.matches("getLastDepthFrame", "android.graphics.Bitmap", listOf("java.lang.String")))
    }

    @Test
    fun panelForegroundRecoveryHooksVerifiedPostVisibilityMethod() {
        val rule = HookProfiles.systemUi
            .first { it.className == "com.android.keyguard.panel.KeyguardPanelViewController" }
            .rules
            .first { it.action == HookAction.OBSERVE_VIDEO_DEPTH_CHAIN }

        assertTrue(rule.matches("updateKeyguardElementsVisibility", "void", emptyList()))
        assertFalse(rule.matches("updateKeyguardElementsVisibility", "boolean", emptyList()))
        assertFalse(rule.matches("updateKeyguardElementsVisibility", "void", listOf("boolean")))
    }

    @Test
    fun depthCallbackRulesMatchSuccessAndFailureAidlMethods() {
        val rule = HookProfiles.miWallpaper
            .flatMap { it.rules }
            .first { it.action == HookAction.OBSERVE_VIDEO_DEPTH_CALLBACK }

        assertTrue(rule.matches("onGetLastDepthFrameSuccess", "void", listOf("android.graphics.Bitmap", "int")))
        assertTrue(rule.matches("onGetLastDepthFrameFailed", "void", listOf("int", "java.lang.String")))
        assertFalse(rule.matches("onGetLastDepthFrameSuccess", "void", listOf("android.graphics.Bitmap")))
    }

    @Test
    fun superWallpaperEngineRuleOnlyMatchesDepthOrTextureDataMethods() {
        val rule = HookProfiles.miWallpaper
            .flatMap { it.rules }
            .first { it.action == HookAction.OBSERVE_SUPER_WALLPAPER_ENGINE }

        assertTrue(rule.matches("updateDepthTexture", "void", listOf("java.nio.ByteBuffer")))
        assertTrue(rule.matches("getDepthBitmap", "android.graphics.Bitmap", emptyList()))
        assertFalse(rule.matches("onOffsetsChanged", "void", listOf("float", "float", "float", "float", "int", "int")))
    }

    @Test
    fun superWallpaperEngineRuleAlsoMatchesRenderFrameCallbacks() {
        val rule = HookProfiles.miWallpaper
            .flatMap { it.rules }
            .first { it.action == HookAction.OBSERVE_SUPER_WALLPAPER_ENGINE }

        assertTrue(rule.matches("onDrawFrame", "void", emptyList()))
        assertTrue(rule.matches("sendFilamentMessage", "void", listOf("java.lang.String")))
    }

    @Test
    fun snowmountainRuntimeRuleMatchesCurrentDeviceRendererSignatures() {
        val rule = HookProfiles.miWallpaper
            .flatMap { it.rules }
            .first { it.action == HookAction.OBSERVE_SUPER_WALLPAPER_RUNTIME }

        assertTrue(rule.matches("d", "void", emptyList()))
        assertTrue(rule.matches("b", "void", listOf("java.lang.String")))
        assertTrue(rule.matches("doFrame", "void", listOf("long")))
        assertTrue(rule.matches("nInit", "void", listOf(
            "int", "int", "java.lang.String", "java.lang.String", "java.lang.String",
            "android.content.res.AssetManager",
        )))
        assertTrue(rule.matches("nSetSurface", "void", listOf("android.view.Surface")))
        assertTrue(rule.matches("nRender", "void", listOf("long")))
        assertTrue(rule.matches("nDestroy", "void", emptyList()))
        assertFalse(rule.matches("nRender", "int", listOf("long")))
        assertFalse(rule.matches("onOffsetsChanged", "void", emptyList()))
    }

    @Test
    fun callbackRegistrationRuleMatchesTheRealAidlCallbackParameter() {
        val rule = HookProfiles.miWallpaper
            .flatMap { it.rules }
            .first { it.action == HookAction.CAPTURE_VIDEO_DEPTH_CALLBACK }

        assertTrue(
            rule.matches(
                "registerLastFrameCallback",
                "void",
                listOf("com.miui.miwallpaper.IMiuiVideoDepthLastFrameCallback"),
            )
        )
        assertFalse(rule.matches("registerLastFrameCallback", "void", listOf("android.os.IBinder")))
    }

    @Test
    fun configuredProfilesDoNotDisableSystemPinEntry() {
        val pinEntryNames = setOf("isEnterPin", "shouldEnterPin", "canEnterPin")
        val configuredRules = (HookProfiles.systemServer + HookProfiles.systemUi).flatMap { it.rules }

        assertFalse(configuredRules.any { rule ->
            rule.action == HookAction.DISABLE_BOOLEAN && rule.names.any { it in pinEntryNames }
        })
    }

    @Test
    fun aodGlassRuleMatchesTheDecompiledCompanionSignature() {
        val rule = HookProfiles.aod
            .flatMap { it.rules }
            .first { it.action == HookAction.ALLOW_GLASS_ON_ANY_WALLPAPER }

        assertTrue(
            rule.matches(
                "glassEffectDisable",
                "boolean",
                listOf("int", "com.miui.keyguard.editor.data.bean.CommonConfig"),
            )
        )
        assertTrue(
            rule.matches(
                "shouldDisableGlassEffect",
                "boolean",
                listOf("int", "com.miui.keyguard.editor.data.bean.CommonConfig"),
            )
        )
        assertFalse(rule.matches("glassEffectDisable", "boolean", listOf("int")))
    }

    @Test
    fun aodGlassPreservationRulesMatchDecompiledSignatures() {
        val rules = HookProfiles.aod.flatMap { it.rules }
        assertTrue(rules.any {
            it.action == HookAction.PRESERVE_GLASS_EFFECT && it.matches(
                "computeSupportedClockEffect",
                "int",
                listOf("com.miui.keyguard.editor.data.bean.CommonConfig", "int"),
            )
        })
        assertTrue(rules.any {
            it.action == HookAction.PRESERVE_GLASS_EFFECT && it.matches(
                "computeSupportedClockEffect",
                "int",
                listOf("com.miui.keyguard.editor.data.bean.CommonConfig", "int", "android.content.Context"),
            )
        })
        assertTrue(rules.any {
            it.action == HookAction.ALLOW_GLASS_WALLPAPER_FILTER && it.matches(
                "isWallpaperSupportGlassFilter",
                "boolean",
                listOf("[Ljava.lang.String;"),
            )
        })
        assertTrue(rules.any {
            it.action == HookAction.ALLOW_GLASS_WALLPAPER_FILTER && it.matches(
                "isWallpaperSupportGlassFilter",
                "boolean",
                listOf("[Ljava/lang/String;"),
            )
        })
        assertTrue(rules.any {
            it.action == HookAction.ALLOW_GLASS_WALLPAPER_FILTER && it.matches(
                "isWallpaperSupportGlassFilter",
                "boolean",
                listOf("java.lang.String"),
            )
        })
        assertTrue(rules.any {
            it.action == HookAction.SKIP_GLASS_FILTER_DISABLE && it.matches(
                "disableGlassFilter",
                "void",
                emptyList(),
            )
        })
        assertTrue(HookProfiles.systemUi.flatMap { it.rules }.any {
            it.action == HookAction.PRESERVE_GLASS_SYSTEMUI && it.matches(
                "setClockBean",
                "void",
                listOf("boolean", "boolean", "com.miui.clock.module.ClockBean"),
            )
        })
        assertTrue(HookProfiles.systemUi.flatMap { it.rules }.any {
            it.action == HookAction.PRESERVE_GLASS_SYSTEMUI && it.matches(
                "getClockBeanFromSetting",
                "com.miui.clock.module.ClockBean",
                listOf("java.lang.String"),
            )
        })
    }

    @Test
    fun superWallpaperVideoDepthRuleMatchesControllerEntrypoints() {
        val rule = HookProfiles.systemUi
            .flatMap { it.rules }
            .first { it.action == HookAction.ENABLE_SUPER_WALLPAPER_VIDEO_DEPTH }

        assertTrue(rule.matches("isWallpaperSupportDepth", "boolean", emptyList()))
        assertTrue(rule.matches("setWallpaperSupportDepth", "void", listOf("boolean")))
        assertTrue(rule.matches("getDepthAvoidRect", "android.graphics.Rect", emptyList()))
        assertFalse(rule.matches("setWallpaperSupportDepth", "boolean", listOf("boolean")))
        assertFalse(rule.matches("getDepthAvoidRect", "void", emptyList()))
    }

    @Test
    fun superWallpaperVideoRenderRuleMatchesKeyguardManagerEntrypoint() {
        val rule = HookProfiles.systemUi
            .flatMap { it.rules }
            .first { it.action == HookAction.ENABLE_SUPER_WALLPAPER_VIDEO_RENDER }

        assertTrue(rule.matches("isDepthVideoEnable", "boolean", emptyList()))
        assertFalse(rule.matches("isDepthVideoEnable", "void", emptyList()))
        assertFalse(rule.matches("isDepthVideoEnable", "boolean", listOf("int")))
    }

    @Test
    fun aodTransitionRuleMatchesOnlyTheVerifiedOs4StaticSignature() {
        val rule = HookProfiles.systemUi
            .first { it.className == "com.android.keyguard.panel.KeyguardPanelViewController" }
            .rules.first { it.action == HookAction.OBSERVE_AOD_CLOCK_TRANSITION }
        val parameters = listOf(
            "com.android.keyguard.panel.KeyguardPanelViewController", "boolean", "java.lang.String", "int",
        )
        assertTrue(rule.matches("linkageViewAnim\$default", "void", parameters))
        assertFalse(rule.matches("linkageViewAnim\$default", "void", parameters.drop(1)))
        assertFalse(rule.matches("linkageViewAnim\$default", "boolean", parameters))
        assertFalse(rule.matches("linkageViewAnim", "void", parameters))
    }

    @Test
    fun superWallpaperDepthFrameRuleMatchesDeviceAsyncProvider() {
        val rule = HookProfiles.systemUi
            .flatMap { it.rules }
            .first { it.action == HookAction.PROVIDE_SUPER_WALLPAPER_DEPTH_FRAME }

        assertTrue(rule.matches("getLastDepthFrameBitmap", "void", listOf("java.util.function.Function")))
        assertTrue(rule.matches("getLastDepthFrameBitmap", "android.graphics.Bitmap", emptyList()))
        assertTrue(rule.matches("getLastDepthFrameBitmap", "android.graphics.Bitmap", listOf("int")))
        assertFalse(rule.matches("getLastDepthFrameBitmap", "void", emptyList()))
        assertFalse(rule.matches("getLastDepthFrameBitmap", "void", listOf("java.lang.Object")))
        assertFalse(rule.matches("getLastDepthFrame", "android.graphics.Bitmap", emptyList()))
    }

    @Test
    fun wallpaperRegistrationRuleMatchesListenerMapOperations() {
        val rule = HookProfiles.miWallpaper
            .flatMap { it.rules }
            .first { it.action == HookAction.OBSERVE_WALLPAPER_REGISTRATION }

        assertTrue(rule.matches("put", "java.lang.Object", listOf("java.lang.Object", "java.lang.Object")))
        assertTrue(rule.matches("remove", "java.lang.Object", listOf("java.lang.Object")))
        assertTrue(rule.matches("remove", "boolean", listOf("java.lang.Object")))
        assertTrue(rule.matches("clear", "void", emptyList()))
        assertFalse(rule.matches("put", "void", listOf("java.lang.Object", "java.lang.Object")))
        assertFalse(rule.matches("remove", "java.lang.Object", emptyList()))
    }

    @Test
    fun aodProfilesContainNoSuperWallpaperEditorHooks() {
        val retiredNames = setOf(
            "bindView", "setSize", "onLayout", "updateClockPositionByTime", "update",
            "initClock", "initTemplateBean", "copyExtendedInfoToClockBean",
            "onMiuiClockViewCreated", "setData", "setBg", "updateAODStyle",
            "updateStyleInfoForPreview", "initStyleInfoSelected", "onColorPickComplete",
            "updateClockColor", "onSlide", "isEditorSetLockWallpaper", "isInValid",
            "supportSuperWallpaperMode", "isSupportDepth", "isSupportHierarchy",
            "supportFilter", "getStyleInfo", "getClockStyleInfo",
        )
        assertFalse(HookProfiles.aod.flatMap { it.rules }.any { rule ->
            rule.names.any(retiredNames::contains)
        })
    }
}
