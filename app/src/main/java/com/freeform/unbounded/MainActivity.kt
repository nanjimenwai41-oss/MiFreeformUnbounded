package com.freeform.unbounded

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeform.unbounded.ui.AboutScreen
import com.freeform.unbounded.ui.BlurredBar
import com.freeform.unbounded.ui.ConfigScreen
import com.freeform.unbounded.ui.FreeformBoundaryScreen
import com.freeform.unbounded.ui.HomeScreen
import com.freeform.unbounded.ui.ThemeScreen
import com.freeform.unbounded.ui.component.FloatingBottomBar
import com.freeform.unbounded.ui.component.FloatingBottomBarItem
import com.freeform.unbounded.ui.component.MainPagerState
import com.freeform.unbounded.ui.component.rememberMainPagerState
import com.freeform.unbounded.ui.theme.FreeformUnboundedTheme
import com.freeform.unbounded.ui.rememberBlurBackdrop
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigRepository.init(this)
        AppSettingsRepository.init(this)
        ModuleStatusRepository.attach()
        enableEdgeToEdge()
        setContent {
            val settings by AppSettingsRepository.settings.collectAsState()
            FreeformUnboundedTheme(settings) {
                FreeformApp(settings = settings)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ModuleStatusRepository.onLifecycleEvent("start")
    }

    override fun onResume() {
        super.onResume()
        ModuleStatusRepository.onLifecycleEvent("resume")
    }

    override fun onPause() {
        ModuleStatusRepository.onLifecycleEvent("pause")
        super.onPause()
    }

    override fun onStop() {
        ModuleStatusRepository.onLifecycleEvent("stop")
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        ModuleStatusRepository.onLifecycleEvent(if (hasFocus) "focus" else "blur")
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        ModuleStatusRepository.onLifecycleEvent("multi-window")
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        ModuleStatusRepository.onLifecycleEvent("picture-in-picture")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        ModuleStatusRepository.onLifecycleEvent("configuration")
    }
}

private sealed interface AppRoute : NavKey {
    data object Main : AppRoute
    data object Theme : AppRoute
    data object FreeformBoundary : AppRoute
}

@Composable
private fun FreeformApp(
    settings: AppSettings,
) {
    var secondaryPage by rememberSaveable { mutableStateOf(SECONDARY_MAIN) }
    val secondaryBackStack = remember(secondaryPage) {
        mutableStateListOf<AppRoute>(AppRoute.Main).apply {
            when (secondaryPage) {
                SECONDARY_THEME -> add(AppRoute.Theme)
                SECONDARY_FREEFORM_BOUNDARY -> add(AppRoute.FreeformBoundary)
            }
        }
    }
    val status by ModuleStatusRepository.status.collectAsState()
    // Keep Tab 2 reachable when the module is inactive; its controls explain why
    // they are unavailable instead of removing the destination.
    val tabs = MainTab.entries
    var savedMainPage by rememberSaveable { mutableStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = savedMainPage.coerceIn(0, tabs.lastIndex),
        pageCount = { tabs.size },
    )
    val mainPagerState = rememberMainPagerState(pagerState = pagerState)

    val popSecondary = { secondaryPage = SECONDARY_MAIN }

    val settledPage = mainPagerState.pagerState.settledPage
    LaunchedEffect(settledPage, tabs.size) {
        savedMainPage = settledPage.coerceIn(0, tabs.lastIndex)
    }

    LaunchedEffect(tabs.size) {
        if (mainPagerState.selectedPage >= tabs.size) {
            mainPagerState.animateToPage(0)
        }
    }

    val background = MiuixTheme.colorScheme.background
    val glassSupported = settings.floatingBottomBarBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val backdrop = rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
    val floating = settings.floatingBottomBar
    Box(Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = secondaryBackStack,
            modifier = Modifier.fillMaxSize(),
            onBack = { popSecondary() },
            entryProvider = entryProvider {
                entry<AppRoute.Main> {
                    MainRoot(
                        settings = settings,
                        tabs = tabs,
                        mainPagerState = mainPagerState,
                        statusInactive = !status.active,
                        moduleReady = status.active || status.pendingRestart,
                        floating = floating,
                        glassSupported = glassSupported,
                        backdrop = backdrop,
                        onOpenTheme = { secondaryPage = SECONDARY_THEME },
                        onOpenFreeformBoundary = { secondaryPage = SECONDARY_FREEFORM_BOUNDARY },
                    )
                }
                entry<AppRoute.Theme> {
                    ThemeScreen(
                        settings = settings,
                        onBack = { popSecondary() },
                    )
                }
                entry<AppRoute.FreeformBoundary> {
                    FreeformBoundaryScreen(
                        enableBlur = settings.enableBlur,
                        moduleReady = status.active || status.pendingRestart,
                        onBack = { popSecondary() },
                    )
                }
            },
        )
    }
}

@Composable
private fun MainScreenBackHandler(
    isBackEnabled: Boolean,
    mainPagerState: MainPagerState,
) {
    val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        isBackEnabled = isBackEnabled,
        state = navigationEventState,
        onBackCompleted = { mainPagerState.animateToPage(0) },
    )
}

@Composable
private fun MainRoot(
    settings: AppSettings,
    tabs: List<MainTab>,
    mainPagerState: MainPagerState,
    statusInactive: Boolean,
    moduleReady: Boolean,
    floating: Boolean,
    glassSupported: Boolean,
    backdrop: LayerBackdrop,
    onOpenTheme: () -> Unit,
    onOpenFreeformBoundary: () -> Unit,
) {
    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    val selectedPage = mainPagerState.selectedPage.coerceIn(0, tabs.lastIndex)
    val blurBackdrop = rememberBlurBackdrop(settings.enableBlur)
    MainScreenBackHandler(
        isBackEnabled = selectedPage != 0,
        mainPagerState = mainPagerState,
    )

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier),
        ) {
            HorizontalPager(
                state = mainPagerState.pagerState,
                beyondViewportPageCount = tabs.lastIndex.coerceAtLeast(0),
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (floating && glassSupported) {
                            Modifier.layerBackdrop(backdrop)
                        } else {
                            Modifier
                        },
                    ),
            ) { page ->
                when (tabs.getOrElse(page) { MainTab.HOME }) {
                    MainTab.HOME -> HomeScreen(
                        enableBlur = settings.enableBlur,
                        floatingBottomBar = floating,
                    )
                    MainTab.CONFIG -> ConfigScreen(
                        onOpenTheme = onOpenTheme,
                        enableBlur = settings.enableBlur,
                        floatingBottomBar = floating,
                        onOpenFreeformBoundary = onOpenFreeformBoundary,
                    )
                    MainTab.ABOUT -> AboutScreen(
                        enableBlur = settings.enableBlur,
                        floatingBottomBar = floating,
                    )
                }
            }
        }

        if (floating) {
            AnimatedContent(
                targetState = tabs,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "bottom-bar-tabs",
                modifier = Modifier.align(Alignment.BottomCenter),
            ) { targetTabs ->
                KernelStyleFloatingBar(
                    settings = settings,
                    tabs = targetTabs,
                    selectedIndex = selectedPage,
                    onSelected = mainPagerState::animateToPage,
                    showStatusBadge = settings.navigationBadge && statusInactive,
                    backdrop = backdrop,
                    glassSupported = glassSupported,
                )
            }
        } else {
            BlurredBar(
                backdrop = blurBackdrop,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                NavigationBar(
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            modifier = Modifier.weight(1f),
                            selected = selectedPage == index,
                            onClick = { mainPagerState.animateToPage(index) },
                            icon = tab.icon,
                            label = tab.label,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KernelStyleFloatingBar(
    settings: AppSettings,
    tabs: List<MainTab>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    showStatusBadge: Boolean,
    backdrop: Backdrop,
    glassSupported: Boolean,
) {
    FloatingBottomBar(
        modifier = Modifier.padding(bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        selectedIndex = { selectedIndex },
        onSelected = onSelected,
        backdrop = backdrop,
        tabsCount = tabs.size,
        isBlurEnabled = glassSupported,
    ) {
        tabs.forEachIndexed { index, tab ->
            FloatingBottomBarItem(
                onClick = { onSelected(index) },
                modifier = Modifier.size(width = 82.dp, height = 56.dp),
            ) {
                if (settings.navigationBadge && showStatusBadge && index == 0) {
                    BadgedBox(badge = { Badge() }) { Icon(tab.icon, contentDescription = tab.label) }
                } else {
                    Icon(tab.icon, contentDescription = tab.label)
                }
                Text(tab.label, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
            }
        }
    }
}

private enum class MainTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("首页", Icons.Rounded.Home),
    CONFIG("设置", Icons.Rounded.Settings),
    ABOUT("关于", Icons.Rounded.Info),
}

private const val SECONDARY_MAIN = 0
private const val SECONDARY_THEME = 1
private const val SECONDARY_FREEFORM_BOUNDARY = 2
