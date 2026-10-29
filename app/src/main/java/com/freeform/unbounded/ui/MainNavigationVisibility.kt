package com.freeform.unbounded.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/** 弹窗跨越内部 Scaffold；由最外层统一控制三种导航的可见性。 */
internal class MainNavigationVisibility {
    private val owners = mutableStateListOf<Any>()
    val hidden: Boolean get() = owners.isNotEmpty()

    fun update(owner: Any, visible: Boolean) {
        if (visible && owner !in owners) owners.add(owner)
        if (!visible) owners.remove(owner)
    }
}

internal val LocalMainNavigationVisibility = staticCompositionLocalOf<MainNavigationVisibility?> { null }

@Composable
internal fun HideMainNavigation(visible: Boolean) {
    val controller = LocalMainNavigationVisibility.current
    val owner = remember { Any() }
    SideEffect { controller?.update(owner, visible) }
    DisposableEffect(controller, owner) {
        onDispose { controller?.update(owner, false) }
    }
}
