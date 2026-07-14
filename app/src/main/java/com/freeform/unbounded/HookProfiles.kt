package com.freeform.unbounded

import android.graphics.Rect
import java.lang.reflect.Method

internal enum class HookAction {
    DISABLE_BOOLEAN,
    EXPAND_RECT_RESULT,
    EXPAND_MOVABLE_RECT,
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
            HookAction.EXPAND_RECT_RESULT -> returnType == Rect::class.java.name &&
                parameterTypes.none { it == Rect::class.java.name + "[]" }
            HookAction.EXPAND_MOVABLE_RECT -> returnType == Rect::class.java.name
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
    private val disablePin = MethodHookRule(
        names = setOf("isEnterPin", "shouldEnterPin", "canEnterPin"),
        action = HookAction.DISABLE_BOOLEAN,
    )

    private val expandConstraint = MethodHookRule(
        names = setOf(
            "getConstraintRect",
            "getFreeformConstraintRect",
            "getFreeformAccessibleArea",
            "getFreeformMoveBounds",
            "getMoveableBounds",
            "getMovableBounds",
        ),
        action = HookAction.EXPAND_RECT_RESULT,
    )

    private val expandMovableBounds = MethodHookRule(
        names = setOf("getMovableBounds"),
        action = HookAction.EXPAND_MOVABLE_RECT,
    )

    val systemServer = listOf(
        "com.android.server.wm.MiuiFreeformWindowController",
        "com.android.server.wm.MiuiFreeformWindowStrategy",
        "com.android.server.wm.MiuiFreeformGestureController",
        "com.miui.server.wm.MiuiFreeformWindowController",
        "com.miui.server.wm.MiuiFreeformWindowStrategy",
        "com.miui.server.wm.MiuiFreeformGestureController",
    ).map { ClassHookProfile(it, listOf(disablePin, expandConstraint)) }

    val systemUi = listOf(
        "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformPinHandler",
        "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModePinHandler",
        "com.android.wm.shell.miui.freeform.MiuiFreeformTaskMotionAlgorithm",
        "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformTaskMotionAlgorithm",
        "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeController",
        "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeGestureHandler",
    ).map { ClassHookProfile(it, listOf(disablePin, expandConstraint)) } +
        ClassHookProfile("com.android.wm.shell.multitasking.common.MultiTaskingDisplayInfo", listOf(expandMovableBounds))
}
