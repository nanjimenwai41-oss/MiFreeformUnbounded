package com.freeform.unbounded

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HookProfilesTest {
    @Test
    fun disablePinRequiresExactNameAndBooleanReturnType() {
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
    fun everyConfiguredProfileHasRules() {
        assertTrue((HookProfiles.systemServer + HookProfiles.systemUi).all { it.rules.isNotEmpty() })
    }
}
