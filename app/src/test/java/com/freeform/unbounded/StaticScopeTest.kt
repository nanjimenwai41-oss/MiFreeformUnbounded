package com.freeform.unbounded

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticScopeTest {
    @Test fun allStaticPackagesAndTheirChildProcessesAreRecognized() {
        RestartScopes.allStatic.forEach { target ->
            assertTrue(RestartScopes.containsProcess(target.packageName))
            assertTrue(RestartScopes.containsProcess("${target.packageName}:wallpaper"))
        }
    }

    @Test fun similarPackageNamesCannotCountAsActiveStaticTargets() {
        listOf(
            "",
            "systemui",
            "com.android.systemui.fake",
            "com.miui.aod.fake",
            "com.miui.miwallpaper.unknown",
            "com.miui.miwallpaper.snowmountain.fake:wallpaper",
            "example.com.miui.miwallpaper",
            "com.freeform.unbounded",
        ).forEach { assertFalse(it, RestartScopes.containsProcess(it)) }
    }
}
