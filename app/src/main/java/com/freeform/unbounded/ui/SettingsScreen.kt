package com.freeform.unbounded.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** 第三个一级页面仅保留入口；所有外观控件在二级页面中调整。 */
@Composable
internal fun SettingsScreen(
    enableBlur: Boolean,
    contentBottomPadding: Dp,
    onOpenAppearance: () -> Unit,
    onOpenModuleInfo: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop(enableBlur)
    val colors = MiuixTheme.colorScheme
    Scaffold(topBar = {
        BlurredBar(backdrop) {
            TopAppBar(title = "设置", largeTitle = "设置", scrollBehavior = scrollBehavior,
                color = if (backdrop != null) Color.Transparent else colors.surface)
        }
    }) { padding ->
        Box(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
                    .scrollEndHaptic().overScrollVertical(),
                contentPadding = PaddingValues(12.dp, padding.calculateTopPadding() + 8.dp, 12.dp, contentBottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        ArrowPreference(title = "外观", summary = "主题、颜色、模糊、导航与手势",
                            startAction = { Icon(Icons.Rounded.Palette, null, Modifier.padding(end = 12.dp), tint = colors.primary) },
                            onClick = onOpenAppearance)
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        ArrowPreference(title = "模块说明", summary = "功能介绍与使用说明",
                            startAction = { Icon(Icons.Rounded.Description, null, Modifier.padding(end = 12.dp), tint = colors.primary) },
                            onClick = onOpenModuleInfo)
                        ArrowPreference(title = "关于", summary = "版本信息与开源项目",
                            startAction = { Icon(Icons.Rounded.Info, null, Modifier.padding(end = 12.dp), tint = colors.primary) },
                            onClick = onOpenAbout)
                    }
                }
            }
        }
    }
}
