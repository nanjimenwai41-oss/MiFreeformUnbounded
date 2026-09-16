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
    fun depthCallbackRulesMatchSuccessAndFailureAidlMethods() {
        val rule = HookProfiles.miWallpaper
            .flatMap { it.rules }
            .first { it.action == HookAction.OBSERVE_VIDEO_DEPTH_CALLBACK }

        assertTrue(rule.matches("onGetLastDepthFrameSuccess", "void", listOf("android.graphics.Bitmap", "int")))
        assertTrue(rule.matches("onGetLastDepthFrameFailed", "void", listOf("int", "java.lang.String")))
        assertFalse(rule.matches("onGetLastDepthFrameSuccess", "void", listOf("android.graphics.Bitmap")))
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
