package com.freeform.unbounded

import android.graphics.Rect
import java.lang.reflect.Method

internal enum class HookAction {
    DISABLE_BOOLEAN,
    EXPAND_RECT_RESULT,
    EXPAND_MOVABLE_RECT,
    FREE_HORIZONTAL_FRICTION,
    PRESERVE_HORIZONTAL_DRAG_SESSION,
    PRESERVE_HORIZONTAL_STABLE_OFFSET,
    PRESERVE_HORIZONTAL_ANIM_TARGET_PARAM,
    PRESERVE_HORIZONTAL_MOVE_FINAL_BOUNDS,
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
            HookAction.EXPAND_MOVABLE_RECT -> returnType == Rect::class.java.name &&
                parameterTypes == listOf("android.content.Context")
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
    private val expandConstraint = MethodHookRule(
        names = setOf(
            "getConstraintRect",
            "getFreeformConstraintRect",
            "getFreeformAccessibleArea",
            "getFreeformMoveBounds",
            "getMoveableBounds",
        ),
        action = HookAction.EXPAND_RECT_RESULT,
    )

    private val expandMovableBounds = MethodHookRule(
        names = setOf("getMovableBounds"),
        action = HookAction.EXPAND_MOVABLE_RECT,
    )

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

    val systemServer = listOf(
        "com.android.server.wm.MiuiFreeformWindowController",
        "com.android.server.wm.MiuiFreeformWindowStrategy",
        "com.android.server.wm.MiuiFreeformGestureController",
        "com.miui.server.wm.MiuiFreeformWindowController",
        "com.miui.server.wm.MiuiFreeformWindowStrategy",
        "com.miui.server.wm.MiuiFreeformGestureController",
    ).map { ClassHookProfile(it, listOf(expandConstraint)) }

    val systemUi = listOf(
        "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformPinHandler",
        "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModePinHandler",
        "com.android.wm.shell.miui.freeform.MiuiFreeformTaskMotionAlgorithm",
        "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformTaskMotionAlgorithm",
        "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeController",
        "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeGestureHandler",
    ).map { ClassHookProfile(it, listOf(expandConstraint)) } +
        ClassHookProfile("com.android.wm.shell.multitasking.common.MultiTaskingDisplayInfo", listOf(expandMovableBounds)) +
        ClassHookProfile(
            "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeController",
            listOf(preserveHorizontalDragSession),
        ) +
        ClassHookProfile(
            "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeUtils",
            listOf(freeHorizontalFriction, preserveHorizontalStableOffset),
        ) +
        ClassHookProfile(
            "com.android.wm.shell.multitasking.common.animation.MultiTaskingAnimTarget",
            listOf(preserveHorizontalAnimTargetParam),
        ) +
        ClassHookProfile(
            "com.android.wm.shell.multitasking.miuifreeform.MiuiFreeformModeMoveHandler",
            listOf(preserveHorizontalMoveFinalBounds),
        )
}
