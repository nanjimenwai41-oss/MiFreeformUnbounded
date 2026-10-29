package com.freeform.unbounded.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.state.ToggleableState
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.freeform.unbounded.RestartScope
import com.freeform.unbounded.RestartScopes

@Composable
internal fun RestartTargetsDialog(
    show: Boolean,
    currentWallpaper: RestartScope?,
    onDismiss: () -> Unit,
    onRestart: (List<RestartScope>) -> Unit,
) {
    val targets = remember(currentWallpaper) {
        buildList {
            currentWallpaper?.let(::add)
            RestartScopes.allStatic.filterNot { it == currentWallpaper }.forEach(::add)
        }
    }
    var selected by remember { mutableStateOf(emptySet<RestartScope>()) }
    LaunchedEffect(show, currentWallpaper) {
        if (show) {
            selected = RestartScopes.currentScopes(currentWallpaper)
        }
    }

    OverlayDialog(
        show = show,
        title = "选择重启目标",
        summary = currentWallpaper?.let { "当前使用：${it.label}，已置顶。" }
            ?: "当前未使用可识别的超级壁纸，可手动选择作用域。",
        onDismissRequest = onDismiss,
        maxWidth = 520.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.62f).dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("快速组合", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.SemiBold)
                PresetRow(
                    first = "系统界面",
                    second = "系统 + 锁屏",
                    onFirst = { selected = setOf(RestartScopes.SYSTEM_UI) },
                    onSecond = { selected = setOf(RestartScopes.SYSTEM_UI, RestartScopes.AOD) },
                )
                PresetRow(
                    first = "当前壁纸",
                    second = "全部壁纸",
                    onFirst = {
                        selected = RestartScopes.currentWallpapers(currentWallpaper)
                    },
                    onSecond = { selected = setOf(RestartScopes.MI_WALLPAPER) + RestartScopes.wallpapers },
                )
                PresetRow(
                    first = "当前作用域",
                    second = "全部作用域",
                    onFirst = { selected = RestartScopes.currentScopes(currentWallpaper) },
                    onSecond = { selected = RestartScopes.allStatic.toSet() },
                )

                Text("作用域", style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.SemiBold)
                Card(modifier = Modifier.fillMaxWidth()) {
                    targets.forEach { target ->
                        val isCurrent = target == currentWallpaper
                        RestartTargetRow(
                            scope = target,
                            isCurrent = isCurrent,
                            checked = target in selected,
                            onToggle = {
                                selected = if (target in selected) selected - target else selected + target
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(text = "清空", onClick = { selected = emptySet() }, enabled = selected.isNotEmpty())
                TextButton(text = "取消", onClick = onDismiss)
                TextButton(
                    text = "重启所选（${selected.size}）",
                    onClick = { onRestart(targets.filter { it in selected }) },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun PresetRow(
    first: String,
    second: String,
    onFirst: () -> Unit,
    onSecond: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            text = first,
            onClick = onFirst,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
        TextButton(
            text = second,
            onClick = onSecond,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}

@Composable
private fun RestartTargetRow(
    scope: RestartScope,
    isCurrent: Boolean,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = scope.label + if (isCurrent) "  ·  当前使用" else "",
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
            )
            Text(
                text = when (scope) {
                    RestartScopes.SYSTEM_UI -> "状态栏、自由窗口与锁屏景深"
                    RestartScopes.AOD -> "息屏显示与锁屏时钟"
                    RestartScopes.MI_WALLPAPER -> "系统壁纸服务"
                    else -> if (isCurrent) "正在运行的超级壁纸" else "超级壁纸服务"
                },
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Checkbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            onClick = null,
        )
    }
}
