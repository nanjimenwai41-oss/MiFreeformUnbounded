package com.freeform.unbounded.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.freeform.unbounded.clampPredictiveBackProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Spring used by KernelSU for pager navigation and predictive-back settling. */
private val PagerNavigationSpringSpec: SpringSpec<Float> = spring(
    stiffness = 322.2f,
    dampingRatio = 32.31f / (2f * kotlin.math.sqrt(322.2f)),
    visibilityThreshold = 0.5f,
)

internal class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null
    private var predictiveBackJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        val target = targetIndex.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
        val alreadySettled = target == selectedPage &&
            pagerState.currentPage == target &&
            abs(pagerState.currentPageOffsetFraction) < 0.001f
        if (alreadySettled) return

        predictiveBackJob?.cancel()
        predictiveBackJob = null
        navJob?.cancel()

        selectedPage = target
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.springAnimateToPage(target)
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != target) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    /** Applies the bounded part of a system predictive-back gesture to the current page. */
    fun setPredictiveBackProgress(progress: Float, maxProgress: Float) {
        if (selectedPage <= 0) return

        navJob?.cancel()
        navJob = null
        isNavigating = false
        predictiveBackJob?.cancel()
        val page = pagerState.currentPage.coerceAtLeast(1)
        val offset = -clampPredictiveBackProgress(progress, maxProgress)
        predictiveBackJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.scrollToPage(page, pageOffsetFraction = offset)
            } finally {
                if (predictiveBackJob == myJob) {
                    predictiveBackJob = null
                }
            }
        }
    }

    /** Returns the page to its snapped position when the system gesture is cancelled. */
    fun cancelPredictiveBack() {
        predictiveBackJob?.cancel()
        predictiveBackJob = null
        if (selectedPage > 0) {
            animateToPage(selectedPage)
        }
    }

    fun syncPage() {
        if (!isNavigating && predictiveBackJob == null && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

private suspend fun PagerState.springAnimateToPage(target: Int) {
    if (target !in 0 until pageCount) return
    var shouldSnapToTarget = false
    scroll(MutatePriority.UserInput) {
        val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
        val distance = target - currentPage - currentPageOffsetFraction
        val scrollPixels = distance * pageSize
        if (abs(scrollPixels) <= 0.5f) return@scroll

        var consumedScroll = 0f
        var skipScroll = false
        Animatable(0f).animateTo(
            targetValue = scrollPixels,
            animationSpec = PagerNavigationSpringSpec,
        ) {
            if (skipScroll) return@animateTo

            val delta = value - consumedScroll
            if (abs(delta) > 0.5f) {
                val consumed = scrollBy(delta)
                consumedScroll += consumed
                if (abs(delta - consumed) > 0.1f) {
                    shouldSnapToTarget = true
                    skipScroll = true
                }
            } else {
                consumedScroll = value
            }

            if (abs(velocity) < 0.1f && abs(scrollPixels - consumedScroll) < 1.0f) {
                skipScroll = true
            }
        }

        val remaining = scrollPixels - consumedScroll
        if (abs(remaining) > 0.5f) {
            scrollBy(remaining)
        }
    }

    if (shouldSnapToTarget || currentPage != target) {
        scrollToPage(target)
    }
}

@Composable
internal fun rememberMainPagerState(
    pagerState: PagerState = rememberPagerState(pageCount = { 1 }),
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MainPagerState {
    return remember(pagerState, coroutineScope) {
        MainPagerState(pagerState, coroutineScope)
    }
}
