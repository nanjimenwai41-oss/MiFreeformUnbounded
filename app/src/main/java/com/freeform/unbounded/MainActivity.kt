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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.pager.PagerDefaults
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
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeform.unbounded.ui.AboutScreen
import com.freeform.unbounded.ui.AboutModuleInfo
import com.freeform.unbounded.ui.SettingsScreen
import com.freeform.unbounded.ui.BlurredBar
import com.freeform.unbounded.ui.ConfigScreen
import com.freeform.unbounded.ui.FreeformBoundaryScreen
import com.freeform.unbounded.ui.HomeScreen
import com.freeform.unbounded.ui.LocalMainNavigationVisibility
import com.freeform.unbounded.ui.MainNavigationVisibility
import androidx.compose.material.icons.rounded.Tune
import com.freeform.unbounded.ui.ThemeScreen
import com.freeform.unbounded.ui.component.FloatingBottomBar
import com.freeform.unbounded.ui.component.FloatingBottomBarItem
import com.freeform.unbounded.ui.component.MainPagerState
import com.freeform.unbounded.ui.component.rememberMainPagerState
import com.freeform.unbounded.ui.theme.FreeformUnboundedTheme
import com.freeform.unbounded.ui.rememberBlurBackdrop
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.BadgedBox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerFlingTrackerConnection
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigRepository.init(this)
        AppSettingsRepository.init(this)
        ModuleStatusRepository.attach()
        enableEdgeToEdge()
        setContent {
            val settings by AppSettingsRepository.settings.collectAsState()
            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, settings.pageScale) {
                Density(systemDensity.density * settings.pageScale, systemDensity.fontScale)
            }
            CompositionLocalProvider(LocalDensity provides density) {
                FreeformUnboundedTheme(settings) {
                    FreeformApp(settings = settings)
                }
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
    data object ModuleInfo : AppRoute
    data object FreeformBoundary : AppRoute
    data object About : AppRoute
    data object Appearance : AppRoute
}

private val AppBackStackSaver = listSaver<SnapshotStateList<NavKey>, Int>(
    save = { stack -> stack.map { route ->
        when (route) {
            AppRoute.ModuleInfo -> SECONDARY_MODULE_INFO
            AppRoute.FreeformBoundary -> SECONDARY_FREEFORM_BOUNDARY
            AppRoute.About -> SECONDARY_ABOUT
            AppRoute.Appearance -> SECONDARY_APPEARANCE
            else -> SECONDARY_MAIN
        }
    } },
    restore = { saved -> saved.map { value ->
        when (value) {
            SECONDARY_MODULE_INFO -> AppRoute.ModuleInfo
            SECONDARY_FREEFORM_BOUNDARY -> AppRoute.FreeformBoundary
            SECONDARY_ABOUT -> AppRoute.About
            SECONDARY_APPEARANCE -> AppRoute.Appearance
            else -> AppRoute.Main
        }
    }.toMutableStateList<NavKey>() },
)

@Composable
private fun FreeformApp(
    settings: AppSettings,
) {
    val secondaryBackStack = rememberSaveable(saver = AppBackStackSaver) {
        mutableStateListOf<NavKey>(AppRoute.Main)
    }
    val status by ModuleStatusRepository.status.collectAsState()
    // 模块未激活时仍保留设置入口，由具体控件说明不可用原因。
    val tabs = MainTab.entries
    var savedMainPage by rememberSaveable { mutableStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = savedMainPage.coerceIn(0, tabs.lastIndex),
        pageCount = { tabs.size },
    )
    val mainPagerState = rememberMainPagerState(pagerState = pagerState)

    val popSecondary: () -> Unit = {
        if (secondaryBackStack.size > 1) secondaryBackStack.removeAt(secondaryBackStack.lastIndex)
    }
    val openSecondary: (AppRoute) -> Unit = { route ->
        if (secondaryBackStack.lastOrNull() == AppRoute.Main) secondaryBackStack.add(route)
    }

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
    val glassSupported = settings.useLiquidGlass(Build.VERSION.SDK_INT)
    val swipeDismiss = if (settings.swipeDismiss) NavSwipeDirection.LeftToRight else NavSwipeDirection.None
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
            content = {
                entry<AppRoute.Main> {
                    MainRoot(
                        settings = settings,
                        tabs = tabs,
                        mainPagerState = mainPagerState,
                        showStatusBadge = showModuleStatusBadge(settings.navigationBadge, status),
                        enableMainBack = secondaryBackStack.size == 1,
                        floating = floating,
                        glassSupported = glassSupported,
                        backdrop = backdrop,
                        onOpenModuleInfo = { openSecondary(AppRoute.ModuleInfo) },
                        onOpenFreeformBoundary = { openSecondary(AppRoute.FreeformBoundary) },
                        onOpenAbout = { openSecondary(AppRoute.About) },
                        onOpenAppearance = { openSecondary(AppRoute.Appearance) },
                    )
                }
                entry<AppRoute.ModuleInfo>(swipeDismiss = swipeDismiss) {
                    AboutModuleInfo(
                        enableBlur = settings.enableBlur,
                        contentBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                        onOpenAbout = { secondaryBackStack.add(AppRoute.About) },
                        onBack = { popSecondary() },
                    )
                }
                entry<AppRoute.Appearance>(swipeDismiss = swipeDismiss) {
                    ThemeScreen(
                        settings = settings,
                        contentBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                        onBack = { popSecondary() },
                    )
                }
                entry<AppRoute.FreeformBoundary>(swipeDismiss = swipeDismiss) {
                    FreeformBoundaryScreen(
                        enableBlur = settings.enableBlur,
                        moduleReady = status.active || status.pendingRestart,
                        onBack = { popSecondary() },
                    )
                }
                entry<AppRoute.About>(swipeDismiss = swipeDismiss) {
                    AboutScreen(
                        enableBlur = settings.enableBlur,
                        contentBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
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
    maxPredictiveBackProgress: Float,
) {
    val navigationEventState = rememberNavigationEventState(NavigationEventInfo.None)
    val transitionState = navigationEventState.transitionState
    LaunchedEffect(transitionState, maxPredictiveBackProgress) {
        val inProgress = transitionState as? NavigationEventTransitionState.InProgress
        if (inProgress?.direction == NavigationEventTransitionState.TRANSITIONING_BACK) {
            mainPagerState.setPredictiveBackProgress(
                progress = inProgress.latestEvent.progress,
                maxProgress = maxPredictiveBackProgress,
            )
        }
    }
    NavigationBackHandler(
        isBackEnabled = isBackEnabled,
        state = navigationEventState,
        onBackCancelled = mainPagerState::cancelPredictiveBack,
        onBackCompleted = { mainPagerState.animateToPage(0) },
    )
}

@Composable
private fun MainRoot(
    settings: AppSettings,
    tabs: List<MainTab>,
    mainPagerState: MainPagerState,
    showStatusBadge: Boolean,
    enableMainBack: Boolean,
    floating: Boolean,
    glassSupported: Boolean,
    backdrop: LayerBackdrop,
    onOpenModuleInfo: () -> Unit,
    onOpenFreeformBoundary: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenAppearance: () -> Unit,
) {
    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    val selectedPage = mainPagerState.selectedPage.coerceIn(0, tabs.lastIndex)
    val blurBackdrop = rememberBlurBackdrop(settings.enableBlur)
    val pagerMode = settings.pagerGestureMode
    val interceptPagerGestures = pagerMode == PagerInterceptionMode.CrossAxisInterceptor
    val flingTracker = remember { PagerFlingTrackerConnection() }
    MainScreenBackHandler(
        isBackEnabled = enableMainBack && selectedPage != 0,
        mainPagerState = mainPagerState,
        maxPredictiveBackProgress = settings.maxPredictiveBackProgress,
    )

    val navigationVisibility = remember { MainNavigationVisibility() }
    CompositionLocalProvider(LocalMainNavigationVisibility provides navigationVisibility) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        val startInsets = WindowInsets.systemBars.only(WindowInsetsSides.Start)
        val navigationBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val contentBottomPadding = navigationBottomInset + when {
            useRail -> 16.dp
            floating -> 104.dp
            else -> 88.dp
        }
        Row(Modifier.fillMaxSize()) {
            if (useRail && !navigationVisibility.hidden) {
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            selected = selectedPage == index,
                            onClick = { mainPagerState.animateToPage(index) },
                            icon = tab.icon,
                            label = tab.label,
                            badge = if (showStatusBadge && index == 0) { { Badge() } } else null,
                        )
                    }
                }
            }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .then(if (useRail) Modifier.consumeWindowInsets(startInsets) else Modifier)
                .then(if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier),
        ) {
            HorizontalPager(
                state = mainPagerState.pagerState,
                beyondViewportPageCount = tabs.lastIndex.coerceAtLeast(0),
                userScrollEnabled = !interceptPagerGestures,
                overscrollEffect = null,
                pageNestedScrollConnection = if (interceptPagerGestures) {
                    PagerGestureNestedScrollConnection
                } else {
                    PagerDefaults.pageNestedScrollConnection(mainPagerState.pagerState, Orientation.Horizontal)
                },
                flingBehavior = PagerDefaults.flingBehavior(
                    state = mainPagerState.pagerState,
                    snapAnimationSpec = PagerNavigationSpringSpec,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(flingTracker)
                    .pagerGestureOverride(
                        pagerState = mainPagerState.pagerState,
                        mode = pagerMode,
                        flingTracker = flingTracker,
                    )
                    .then(
                        if (!useRail && floating && glassSupported) {
                            Modifier.layerBackdrop(backdrop)
                        } else {
                            Modifier
                        },
                    ),
            ) { page ->
                when (tabs.getOrElse(page) { MainTab.HOME }) {
                    MainTab.HOME -> HomeScreen(
                        enableBlur = settings.enableBlur,
                        contentBottomPadding = contentBottomPadding,
                        monetEnabled = settings.monetEnabled,
                    )
                    MainTab.CONFIG -> ConfigScreen(
                        enableBlur = settings.enableBlur,
                        contentBottomPadding = contentBottomPadding,
                        onOpenFreeformBoundary = onOpenFreeformBoundary,
                    )
                    MainTab.SETTINGS -> SettingsScreen(
                        enableBlur = settings.enableBlur,
                        contentBottomPadding = contentBottomPadding,
                        onOpenModuleInfo = onOpenModuleInfo,
                        onOpenAbout = onOpenAbout,
                        onOpenAppearance = onOpenAppearance,
                    )
                }
            }
        }
        }

        if (!useRail && floating && !navigationVisibility.hidden) {
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
                    showStatusBadge = showStatusBadge,
                    backdrop = backdrop,
                    glassSupported = glassSupported,
                )
            }
        } else if (!useRail && !navigationVisibility.hidden) {
            BlurredBar(
                backdrop = blurBackdrop,
                modifier = Modifier.align(Alignment.BottomCenter),
                bottom = true,
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
                            badge = if (showStatusBadge && index == 0) { { Badge() } } else null,
                        )
                    }
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
    CONFIG("配置", Icons.Rounded.Tune),
    SETTINGS("设置", Icons.Rounded.Settings),
}

private const val SECONDARY_MAIN = 0
private const val SECONDARY_MODULE_INFO = 1
private const val SECONDARY_FREEFORM_BOUNDARY = 2
private const val SECONDARY_ABOUT = 3
private const val SECONDARY_APPEARANCE = 4
