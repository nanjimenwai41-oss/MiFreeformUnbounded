package com.freeform.unbounded.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import com.freeform.unbounded.AppSettings
import com.freeform.unbounded.ModuleStatus
import com.freeform.unbounded.ModuleConfigKeys
import com.freeform.unbounded.normalizePageScale
import com.freeform.unbounded.showModuleStatusBadge
import com.freeform.unbounded.useLiquidGlass

class FineAdjustmentTest {
    @Test
    fun firstTouchAnchorsWithoutChangingValueAndNextDragUsesQuarterDelta() {
        val state = FineAdjustmentDragState()
        assertEquals(196f, state.adjust(196f, 80f), 0f)
        assertEquals(201f, state.adjust(196f, 100f), 0f)
        assertEquals(199f, state.adjust(201f, 92f), 0f)
    }

    @Test
    fun nextGestureUsesANewAnchorAfterRelease() {
        val state = FineAdjustmentDragState()
        state.adjust(196f, 80f)
        state.finish()
        assertEquals(196f, state.adjust(196f, 300f), 0f)
        assertEquals(191f, state.adjust(196f, 280f), 0f)
    }

    @Test
    fun fineAdjustmentUsesOneQuarterOfTheRawDragDelta() {
        assertEquals(
            105f,
            applyFineAdjustment(
                currentValue = 100f,
                rawValue = 120f,
                previousRawValue = 100f,
            ),
            0f,
        )
    }

    @Test
    fun fineAdjustmentStaysWithinMarginRange() {
        assertEquals(
            ModuleConfigKeys.MAX_MARGIN.toFloat(),
            applyFineAdjustment(
                currentValue = ModuleConfigKeys.MAX_MARGIN - 2f,
                rawValue = 400f,
                previousRawValue = 0f,
            ),
            0f,
        )
    }

    @Test
    fun liquidGlassRequiresEveryUserToggleAndSupportedPlatform() {
        assertEquals(true, AppSettings().useLiquidGlass(33))
        assertEquals(false, AppSettings(enableBlur = false).useLiquidGlass(33))
        assertEquals(false, AppSettings(floatingBottomBarBlur = false).useLiquidGlass(33))
        assertEquals(false, AppSettings().useLiquidGlass(32))
    }

    @Test
    fun statusBadgeIsHiddenWhileCheckingAndAfterActivation() {
        assertEquals(false, showModuleStatusBadge(true, ModuleStatus(checking = true)))
        assertEquals(false, showModuleStatusBadge(true, ModuleStatus(active = true)))
        assertEquals(true, showModuleStatusBadge(true, ModuleStatus(checking = false)))
        assertEquals(false, showModuleStatusBadge(false, ModuleStatus(checking = false)))
    }

    @Test
    fun pageScaleIsBoundedAndRejectsNonFiniteValues() {
        assertEquals(0.8f, normalizePageScale(0.1f), 0f)
        assertEquals(1.1f, normalizePageScale(2f), 0f)
        assertEquals(1f, normalizePageScale(Float.NaN), 0f)
    }
}
