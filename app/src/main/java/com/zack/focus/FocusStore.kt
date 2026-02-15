package com.zack.focus

import android.content.Context
import java.util.Locale
import kotlin.math.max

class FocusStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBlockedPackages(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun setBlockedPackage(packageName: String, blocked: Boolean) {
        val updated = getBlockedPackages().toMutableSet()
        if (blocked) {
            updated.add(packageName)
        } else {
            updated.remove(packageName)
        }
        prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, updated).apply()
    }

    fun startSession25(nowMs: Long) {
        prefs.edit()
            .putLong(KEY_FOCUS_END_TIME_MS, nowMs + SESSION_25_MS)
            .apply()
    }

    fun getSessionEndMs(): Long = prefs.getLong(KEY_FOCUS_END_TIME_MS, 0L)

    fun isSessionActive(nowMs: Long): Boolean = getSessionEndMs() > nowMs

    fun remainingSessionMs(nowMs: Long): Long = max(0L, getSessionEndMs() - nowMs)

    fun initializeDefaultsIfNeeded(installedPackages: Set<String>) {
        if (prefs.getBoolean(KEY_HAS_INITIALIZED_DEFAULTS, false)) return

        val defaults = DEFAULT_BLOCKED_PACKAGES.intersect(installedPackages)

        prefs.edit()
            .putStringSet(KEY_BLOCKED_PACKAGES, defaults)
            .putBoolean(KEY_HAS_INITIALIZED_DEFAULTS, true)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "focus_store"
        private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
        private const val KEY_FOCUS_END_TIME_MS = "focus_end_time_ms"
        private const val KEY_HAS_INITIALIZED_DEFAULTS = "has_initialized_defaults"

        const val SESSION_25_MS = 25 * 60 * 1000L

        val DEFAULT_BLOCKED_PACKAGES: Set<String> = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically"
        )
    }
}

fun formatDurationMmSs(totalMs: Long): String {
    val totalSeconds = max(0L, totalMs) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
