package com.freeform.unbounded

internal const val MIN_PAGE_SCALE = 0.8f
internal const val MAX_PAGE_SCALE = 1.1f

internal fun normalizePageScale(value: Float): Float =
    if (value.isFinite()) value.coerceIn(MIN_PAGE_SCALE, MAX_PAGE_SCALE) else 1f

internal fun AppSettings.useLiquidGlass(sdkInt: Int): Boolean =
    enableBlur && floatingBottomBar && floatingBottomBarBlur && sdkInt >= 33

internal fun showModuleStatusBadge(enabled: Boolean, status: ModuleStatus): Boolean =
    enabled && !status.checking && !status.active
