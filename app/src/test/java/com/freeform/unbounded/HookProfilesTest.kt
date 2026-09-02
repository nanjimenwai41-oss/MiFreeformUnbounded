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
    fun superWallpaperRulesMatchBothClockBindViewOverloadsAndEditorCapabilities() {
        val rules = HookProfiles.aod.flatMap { it.rules }
        val adapt = rules.first { it.action == HookAction.ADAPT_SUPER_WALLPAPER_CLOCK }
        assertTrue(adapt.matches("bindView", "void", emptyList()))
        assertTrue(adapt.matches("bindView", "void", listOf("android.view.View")))
        assertTrue(adapt.matches("setSize", "void", listOf("int")))
        assertTrue(adapt.matches("update", "void", listOf("com.miui.aod.common.StyleInfo", "int")))
        assertTrue(adapt.matches("initClock", "void", emptyList()))
        assertTrue(adapt.matches("copyExtendedInfoToClockBean", "void", listOf(
            "com.miui.clock.module.ClockBean",
            "com.miui.keyguard.editor.data.bean.ClockInfo",
        )))
        assertTrue(adapt.matches("setData", "void", listOf("float", "float", "float", "float", "int")))
        assertTrue(adapt.matches("setBg", "void", listOf("android.view.View", "int")))
        assertTrue(adapt.matches(
            "updateClockColor",
            "void",
            listOf("com.miui.keyguard.editor.edit.color.ColorData"),
        ))
        assertTrue(adapt.matches(
            "onColorPickComplete",
            "void",
            listOf("com.miui.keyguard.editor.edit.color.ColorData", "boolean"),
        ))
        assertFalse(adapt.matches("setBg", "boolean", listOf("android.view.View", "int")))
        assertFalse(adapt.matches("bindView", "int", emptyList()))

        val slide = rules.first { it.action == HookAction.DELEGATE_SUPER_WALLPAPER_SLIDE }
        assertTrue(slide.matches("onSlide", "void", listOf("android.view.View", "float")))
        assertFalse(slide.matches("onSlide", "void", listOf("android.view.View")))

        val editor = rules.first { it.action == HookAction.PRESERVE_SUPER_WALLPAPER_EDITOR }
        assertTrue(editor.matches("supportSuperWallpaperMode", "boolean", emptyList()))
        assertTrue(editor.matches("isSupportDepth", "boolean", emptyList()))
        assertTrue(editor.matches("supportFilter", "boolean", emptyList()))
        assertFalse(editor.matches("supportFilter", "void", emptyList()))
    }

    @Test
    fun systemUiDepthRulesMatchControllerGateAndSetter() {
        val rules = HookProfiles.systemUi.flatMap { it.rules }
        val depth = rules.first { it.action == HookAction.PRESERVE_SUPER_WALLPAPER_DEPTH }
        assertTrue(depth.matches("isWallpaperSupportDepth", "boolean", emptyList()))
        assertTrue(depth.matches("setWallpaperSupportDepth", "void", listOf("boolean")))
        assertFalse(depth.matches("setWallpaperSupportDepth", "boolean", listOf("boolean")))
        assertFalse(depth.matches("isWallpaperSupportDepth", "boolean", listOf("boolean")))
    }

    @Test
    fun superWallpaperEditorProfilesCoverStyleLookupsAndPreviewRefresh() {
        val rules = HookProfiles.aod.flatMap { it.rules }
        val editorRules = rules.filter { it.action == HookAction.PRESERVE_SUPER_WALLPAPER_EDITOR }
        assertTrue(
            editorRules.any { it.matches(
                "getStyleInfo",
                "com.miui.aod.common.StyleInfo",
                listOf("android.content.Context"),
            ) },
        )
        assertTrue(
            editorRules.any { it.matches(
                "getClockStyleInfo",
                "com.miui.aod.common.StyleInfo",
                listOf("android.content.Context"),
            ) },
        )

        assertTrue(
            rules.any { it.action == HookAction.ADAPT_SUPER_WALLPAPER_CLOCK && it.matches(
                "updateAODStyle",
                "com.miui.aod.widget.IAodClock",
                listOf("com.miui.aod.common.StyleInfo"),
            ) },
        )
        assertTrue(
            rules.any { it.action == HookAction.ADAPT_SUPER_WALLPAPER_CLOCK && it.matches(
                "initStyleInfoSelected",
                "com.miui.aod.widget.IAodClock",
                listOf("java.lang.String", "java.lang.String", "android.os.Bundle"),
            ) },
        )
        assertTrue(
            rules.any { it.action == HookAction.ADAPT_SUPER_WALLPAPER_CLOCK && it.matches(
                "updateStyleInfoForPreview",
                "com.miui.aod.widget.IAodClock",
                emptyList(),
            ) },
        )
    }

    @Test
    fun blankSuperWallpaperCategoryUsesTheSameClockAdapter() {
        val profile = HookProfiles.aod.first {
            it.className == "com.miui.aod.category.BlankSuperWallpaperCategoryInfo"
        }
        assertTrue(profile.rules.any { it.action == HookAction.ADAPT_SUPER_WALLPAPER_CLOCK })
        assertTrue(profile.rules.any { it.action == HookAction.PRESERVE_SUPER_WALLPAPER_EDITOR })
    }
}
