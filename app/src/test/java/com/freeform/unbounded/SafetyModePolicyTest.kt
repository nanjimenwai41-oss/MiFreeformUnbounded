package com.freeform.unbounded

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyModePolicyTest {
    @Test
    fun triggersOnTheFourthStartOfTheSameScope() {
        val preferences = MemoryPreferences()

        repeat(3) { index ->
            val observation = SafetyModePolicy.observeProcessStart(
                preferences,
                "com.android.systemui",
                nowMillis = 1_000L + index,
            )
            assertFalse(observation.triggered)
            assertEquals(index + 1, observation.recentStarts)
        }

        val fourth = SafetyModePolicy.observeProcessStart(
            preferences,
            "com.android.systemui",
            nowMillis = 1_003L,
        )

        assertTrue(fourth.triggered)
        assertTrue(SafetyModePolicy.isEnabled(preferences))
        assertEquals(4, fourth.recentStarts)
    }

    @Test
    fun expiredStartsDoNotTrigger() {
        val preferences = MemoryPreferences()
        SafetyModePolicy.observeProcessStart(preferences, "com.android.systemui", 0L)
        SafetyModePolicy.observeProcessStart(preferences, "com.android.systemui", 10L)
        SafetyModePolicy.observeProcessStart(preferences, "com.android.systemui", 20L)

        val observation = SafetyModePolicy.observeProcessStart(
            preferences,
            "com.android.systemui",
            SafetyModePolicy.WINDOW_MILLIS + 21L,
        )

        assertFalse(observation.triggered)
        assertEquals(1, observation.recentStarts)
    }

    @Test
    fun scopesHaveIndependentHistories() {
        val preferences = MemoryPreferences()
        repeat(3) { index ->
            SafetyModePolicy.observeProcessStart(preferences, "com.android.systemui", index.toLong())
        }

        val observation = SafetyModePolicy.observeProcessStart(
            preferences,
            "com.miui.miwallpaper.snowmountain:snowmountainSuperWallpaper",
            3L,
        )

        assertFalse(observation.triggered)
        assertEquals(1, observation.recentStarts)
        assertFalse(SafetyModePolicy.isEnabled(preferences))
    }

    @Test
    fun processNamesWithDifferentSeparatorsDoNotShareHistory() {
        val preferences = MemoryPreferences()
        repeat(3) { index ->
            SafetyModePolicy.observeProcessStart(
                preferences,
                "com.miui.miwallpaper.foo",
                nowMillis = index.toLong(),
            )
        }

        val observation = SafetyModePolicy.observeProcessStart(
            preferences,
            "com.miui.miwallpaper:foo",
            nowMillis = 3L,
        )

        assertFalse(observation.triggered)
        assertEquals(1, observation.recentStarts)
        assertFalse(SafetyModePolicy.isEnabled(preferences))
    }

    @Test
    fun manualDecisionClearsAutoTriggerAndHistory() {
        val preferences = MemoryPreferences()
        repeat(4) { index ->
            SafetyModePolicy.observeProcessStart(preferences, "com.android.systemui", index.toLong())
        }

        SafetyModePolicy.setManualState(preferences, false)
        val observation = SafetyModePolicy.observeProcessStart(
            preferences,
            "com.android.systemui",
            5L,
        )

        assertFalse(SafetyModePolicy.isEnabled(preferences))
        assertFalse(observation.triggered)
        assertEquals(1, observation.recentStarts)
        assertFalse(preferences.getBoolean(ModuleConfigKeys.SAFETY_MODE_AUTO_TRIGGERED, false))
    }

    @Test
    fun readOnlyRemotePreferencesFailClosedWithoutClaimingDurableAutoTrigger() {
        val failures = mutableListOf<Throwable>()
        val preferences = ReadOnlyPreferences()

        repeat(4) { index ->
            val observation = SafetyModePolicy.observeProcessStart(
                preferences,
                "com.android.systemui",
                nowMillis = index.toLong(),
                onPersistenceFailure = failures::add,
            )
            assertTrue(observation.enabled)
            assertFalse(observation.triggered)
            assertFalse(observation.historyPersisted)
        }

        assertEquals(4, failures.size)
    }

    @Test
    fun falseCommitFailsClosedAndReportsPersistenceFailure() {
        val failures = mutableListOf<Throwable>()
        val preferences = MemoryPreferences(commitResult = false)

        val observation = SafetyModePolicy.observeProcessStart(
            preferences,
            "com.android.systemui",
            nowMillis = 1L,
            onPersistenceFailure = failures::add,
        )

        assertTrue(observation.enabled)
        assertFalse(observation.triggered)
        assertFalse(observation.historyPersisted)
        assertEquals(1, failures.size)
    }

    @Test
    fun regularConfigWritePreservesSafetyModeOwnedValues() {
        val preferences = MemoryPreferences()
        preferences.edit()
            .putBoolean(ModuleConfigKeys.SAFETY_MODE_ENABLED, true)
            .putBoolean(ModuleConfigKeys.SAFETY_MODE_AUTO_TRIGGERED, true)
            .commit()

        AppConfig(securityMarginPx = 220).writeTo(
            preferences.edit(),
            includeSafetyMode = false,
        ).commit()

        assertTrue(SafetyModePolicy.isEnabled(preferences))
        assertTrue(preferences.getBoolean(ModuleConfigKeys.SAFETY_MODE_AUTO_TRIGGERED, false))
        assertEquals(220, preferences.getInt(ModuleConfigKeys.SECURITY_MARGIN, 0))
    }

    private open class MemoryPreferences(
        private val commitResult: Boolean = true,
    ) : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) removals += key }
            override fun clear(): SharedPreferences.Editor = apply { values.clear() }
            override fun commit(): Boolean {
                removals.forEach(values::remove)
                values.putAll(pending)
                return commitResult
            }
            override fun apply() { commit() }
        }
    }

    private class ReadOnlyPreferences : MemoryPreferences() {
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException(
            "Read only implementation",
        )
    }
}
