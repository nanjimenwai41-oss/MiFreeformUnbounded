package com.freeform.unbounded

import android.content.SharedPreferences

/**
 * Process-local crash-loop guard shared through the Xposed remote preference
 * store. A single normal scope restart is harmless; only repeated loads of
 * the same target process inside a short window trip the global bypass.
 */
internal object SafetyModePolicy {
    const val WINDOW_MILLIS = 60_000L
    const val START_THRESHOLD = 4

    data class StartObservation(
        val tracked: Boolean,
        val enabled: Boolean,
        val triggered: Boolean,
        val recentStarts: Int,
        val historyPersisted: Boolean = false,
    )

    fun observeProcessStart(
        preferences: SharedPreferences?,
        processName: String,
        nowMillis: Long = System.currentTimeMillis(),
        onPersistenceFailure: (Throwable) -> Unit = {},
    ): StartObservation {
        if (!isTrackedProcess(processName)) {
            return StartObservation(
                tracked = false,
                enabled = runCatching { isEnabled(preferences) }.getOrDefault(false),
                triggered = false,
                recentStarts = 0,
            )
        }
        val prefs = preferences ?: return unavailableObservation(0)
        val safetyEnabled = runCatching { isEnabled(prefs) }.getOrElse { error ->
            onPersistenceFailure(error)
            return unavailableObservation(0)
        }
        if (safetyEnabled) {
            return StartObservation(true, true, false, 0, true)
        }
        val key = historyKey(processName)
        val recent = runCatching { parseHistory(prefs.getString(key, null), nowMillis) }
            .getOrElse { error ->
                onPersistenceFailure(error)
                return unavailableObservation(0)
            }
        val updated = (recent + nowMillis).takeLast(START_THRESHOLD)
        val triggered = updated.size >= START_THRESHOLD
        val editor = runCatching {
            prefs.edit().putString(key, updated.joinToString(","))
        }.getOrElse { error ->
            onPersistenceFailure(error)
            return unavailableObservation(updated.size)
        }
        if (triggered) {
            editor
                .putBoolean(ModuleConfigKeys.SAFETY_MODE_ENABLED, true)
                .putBoolean(ModuleConfigKeys.SAFETY_MODE_AUTO_TRIGGERED, true)
                .putString(ModuleConfigKeys.SAFETY_MODE_TRIGGER_SCOPE, processName)
        }
        val persisted = runCatching { editor.commit() }
            .onFailure(onPersistenceFailure)
            .getOrDefault(false)
        if (!persisted) {
            onPersistenceFailure(IllegalStateException("Remote safety history commit returned false"))
            return unavailableObservation(updated.size)
        }
        // A read-only remote preference must never make the hook callback throw,
        // and a threshold reached in memory is not a durable global safety mode
        // unless the enabled bit was committed. The current target still fails
        // closed so a broken persistence channel cannot run unstable hooks.
        return StartObservation(
            tracked = true,
            enabled = triggered,
            triggered = triggered,
            recentStarts = updated.size,
            historyPersisted = true,
        )
    }

    fun isEnabled(preferences: SharedPreferences?): Boolean =
        preferences?.getBoolean(ModuleConfigKeys.SAFETY_MODE_ENABLED, false) == true

    /** Applies a user decision and clears stale crash-loop history. */
    /**
     * Returns whether the requested state was durably accepted by the backing
     * preference store. RemotePreferences.commit() can return false when the
     * service is unavailable, so callers must not report a successful disable
     * without checking this result.
     */
    fun setManualState(preferences: SharedPreferences?, enabled: Boolean): Boolean {
        val prefs = preferences ?: return false
        return runCatching {
            val editor = prefs.edit()
                .putBoolean(ModuleConfigKeys.SAFETY_MODE_ENABLED, enabled)
                .putBoolean(ModuleConfigKeys.SAFETY_MODE_AUTO_TRIGGERED, false)
                .remove(ModuleConfigKeys.SAFETY_MODE_TRIGGER_SCOPE)
            clearHistory(editor, prefs)
            editor.commit()
        }.getOrDefault(false)
    }

    fun clearHistory(preferences: SharedPreferences?) {
        val prefs = preferences ?: return
        clearHistory(prefs.edit(), prefs).commit()
    }

    fun isTrackedProcess(processName: String): Boolean =
        processName == "com.android.systemui" ||
            processName.startsWith("com.android.systemui:") ||
            processName == "com.miui.aod" ||
            processName.startsWith("com.miui.aod:") ||
            processName == "com.miui.miwallpaper" ||
            processName.startsWith("com.miui.miwallpaper.") ||
            processName.startsWith("com.miui.miwallpaper:")

    private fun clearHistory(
        editor: SharedPreferences.Editor,
        preferences: SharedPreferences,
    ): SharedPreferences.Editor {
        preferences.all.keys
            .filter { it.startsWith(ModuleConfigKeys.SAFETY_MODE_HISTORY_PREFIX) }
            .forEach(editor::remove)
        return editor
    }

    private fun historyKey(processName: String): String =
        ModuleConfigKeys.SAFETY_MODE_HISTORY_PREFIX +
            processName.map { character ->
                character.code.toString(16).padStart(4, '0')
            }.joinToString("")

    private fun unavailableObservation(recentStarts: Int) =
        StartObservation(
            tracked = true,
            enabled = true,
            triggered = false,
            recentStarts = recentStarts,
            historyPersisted = false,
        )

    private fun parseHistory(value: String?, nowMillis: Long): List<Long> =
        value.orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }
            .filter { timestamp ->
                val age = nowMillis - timestamp
                age in 0..WINDOW_MILLIS
            }
            .takeLast(START_THRESHOLD - 1)
}
