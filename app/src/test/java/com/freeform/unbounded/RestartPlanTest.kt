package com.freeform.unbounded

import org.junit.Assert.*
import org.junit.Test

class RestartPlanTest {
    @Test fun allNineTargetsArePreservedWithSystemUiLast() {
        val targets = RestartPlan.ordered(RestartScopes.allStatic + RestartScopes.SNOW_MOUNTAIN)
        assertEquals(9, targets.size)
        assertEquals(RestartScopes.SYSTEM_UI, targets.last())
        assertEquals(RestartScopes.allStatic.toSet(), targets.toSet())
    }

    @Test fun restartDoesNotStopPackagesOrMissColonProcesses() {
        val script = RestartPlan.script(RestartScopes.allStatic)
        assertFalse(script.contains("force-stop"))
        assertTrue(script.contains("pkg \":\""))
        RestartScopes.allStatic.forEach { assertTrue(script.contains("restart_target '${it.id}' '${it.packageName}'")) }
    }

    @Test fun incompleteShellExecutionCannotReportAllNineSucceeded() {
        val results = RestartPlan.results("FREEFORM_RESULT aod restarted\nFREEFORM_RESULT moon inactive", RestartScopes.allStatic)
        assertEquals(9, results.size)
        assertEquals(1, results.count { it.value == "restarted" })
        assertEquals(1, results.count { it.value == "inactive" })
        assertEquals(7, results.count { it.value == "failed" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun untrustedTargetsCannotEnterTheRootScript() {
        RestartPlan.script(listOf(RestartScope("bad", "bad", "bad; reboot")))
    }

    @Test fun currentPresetIncludesOnlyActiveWallpaperAndCoreScopes() {
        assertEquals(setOf(RestartScopes.SYSTEM_UI, RestartScopes.AOD, RestartScopes.MI_WALLPAPER), RestartScopes.currentScopes(null))
        assertEquals(4, RestartScopes.currentScopes(RestartScopes.SNOW_MOUNTAIN).size)
        assertFalse(RestartScopes.MOON in RestartScopes.currentScopes(RestartScopes.SNOW_MOUNTAIN))
    }
}
