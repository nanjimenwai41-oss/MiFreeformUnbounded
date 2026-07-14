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
    fun constraintRequiresExactNameAndRectReturnType() {
        val rule = MethodHookRule(setOf("getConstraintRect"), HookAction.EXPAND_RECT_RESULT)

        assertTrue(rule.matches("getConstraintRect", "android.graphics.Rect", emptyList()))
        assertFalse(rule.matches("calculateConstraintRect", "android.graphics.Rect", emptyList()))
        assertFalse(rule.matches("getConstraintRect", "void", listOf("android.graphics.Rect")))
    }

    @Test
    fun movableBoundsRuleOnlyMatchesContextOverload() {
        val rule = MethodHookRule(setOf("getMovableBounds"), HookAction.EXPAND_MOVABLE_RECT)

        assertTrue(rule.matches("getMovableBounds", "android.graphics.Rect", listOf("android.content.Context")))
        assertFalse(
            rule.matches(
                "getMovableBounds",
                "android.graphics.Rect",
                listOf("android.content.Context", "int", "int", "int"),
            )
        )
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
    fun everyConfiguredProfileHasRules() {
        assertTrue((HookProfiles.systemServer + HookProfiles.systemUi).all { it.rules.isNotEmpty() })
    }

    @Test
    fun configuredProfilesDoNotDisableSystemPinEntry() {
        val pinEntryNames = setOf("isEnterPin", "shouldEnterPin", "canEnterPin")
        val configuredRules = (HookProfiles.systemServer + HookProfiles.systemUi).flatMap { it.rules }

        assertFalse(configuredRules.any { rule ->
            rule.action == HookAction.DISABLE_BOOLEAN && rule.names.any { it in pinEntryNames }
        })
    }
}
