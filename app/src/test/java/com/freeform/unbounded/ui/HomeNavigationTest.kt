package com.freeform.unbounded.ui

import com.freeform.unbounded.RestartScopes
import org.junit.Assert.*
import org.junit.Test

class HomeNavigationTest {
    @Test fun onlyCurrentSuperWallpaperIsVisible() {
        RestartScopes.wallpapers.forEach { current ->
            assertEquals(listOf(current), visibleVersionScopes(current).filter { it in RestartScopes.wallpapers })
            assertEquals(5, moreVersionScopes(current).size)
            assertFalse(current in moreVersionScopes(current))
        }
    }

    @Test fun noSuperWallpaperHidesAllSixInMore() {
        assertTrue(visibleVersionScopes(null).none { it in RestartScopes.wallpapers })
        assertEquals(RestartScopes.wallpapers, moreVersionScopes(null))
    }

    @Test fun closingOneDialogCannotExposeNavigationUnderAnother() {
        val state = MainNavigationVisibility()
        val restart = Any()
        val more = Any()
        state.update(restart, true)
        state.update(more, true)
        state.update(restart, false)
        assertTrue(state.hidden)
        state.update(more, false)
        assertFalse(state.hidden)
    }
}
