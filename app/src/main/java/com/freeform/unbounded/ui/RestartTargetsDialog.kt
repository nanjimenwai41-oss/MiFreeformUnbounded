package com.freeform.unbounded.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.text.style.TextOverflow
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
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
import androidx.compose.runtime.withFrameNanos
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
    val scrollState = rememberScrollState()
    LaunchedEffect(show, currentWallpaper) {
        if (show) {
            selected = RestartScopes.currentScopes(currentWallpaper)
            // OverlayDialog 保留内容状态；等待布局后复位，避免再次打开停留在作用域列表底部。
            withFrameNanos { }
            scrollState.scrollTo(0)
        }
    }

    var hidingNavigation by remember { mutableStateOf(show) }
    LaunchedEffect(show) { if (show) hidingNavigation = true }
    HideMainNavigation(show || hidingNavigation)
    OverlayDialog(
        show = show,
        title = "选择重启目标",
        summary = currentWallpaper?.let { "当前使用：${it.label}，已置顶。" }
            ?: "当前未使用可识别的超级壁纸，可手动选择作用域。",
        onDismissRequest = onDismiss,
        onDismissFinished = { hidingNavigation = false },
        maxWidth = 520.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.62f).dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("快速组合", modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.SemiBold)
                    TextButton(text = "清空", onClick = { selected = emptySet() }, enabled = selected.isNotEmpty())
                }
                val presets = listOf(
                    "系统界面" to setOf(RestartScopes.SYSTEM_UI),
                    "系统与锁屏" to setOf(RestartScopes.SYSTEM_UI, RestartScopes.AOD),
                    "当前壁纸" to RestartScopes.currentWallpapers(currentWallpaper),
                    "全部壁纸" to (setOf(RestartScopes.MI_WALLPAPER) + RestartScopes.wallpapers),
                    "当前作用域" to RestartScopes.currentScopes(currentWallpaper),
                    "全部作用域" to RestartScopes.allStatic.toSet(),
                )
                presets.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, targets) ->
                            PresetButton(label, selected == targets, { selected = targets }, Modifier.weight(1f))
                        }
                    }
                }

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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    text = "重启（${selected.size}）",
                    modifier = Modifier.weight(1f),
                    onClick = { onRestart(targets.filter { it in selected }) },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun PresetButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MiuixTheme.colorScheme
    Card(
        modifier = modifier,
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
        colors = CardDefaults.defaultColors(
            color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        ),
        onClick = onClick,
        showIndication = true,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Medium)
            AnimatedVisibility(selected, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.8f)) {
                Icon(Icons.Rounded.Check, "已选择", Modifier.size(20.dp), tint = colors.primary)
            }
        }
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
