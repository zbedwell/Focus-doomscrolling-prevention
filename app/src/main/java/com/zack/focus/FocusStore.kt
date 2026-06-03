package com.zack.focus

import android.content.Context
import java.util.Locale
import kotlin.math.max

class FocusStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isFocusModeActive(): Boolean = prefs.getBoolean(KEY_FOCUS_ACTIVE, false)

    fun setFocusModeActive(active: Boolean) {
        val edit = prefs.edit().putBoolean(KEY_FOCUS_ACTIVE, active)
        if (active) edit.putLong(KEY_FOCUS_START_MS, System.currentTimeMillis())
        edit.apply()
    }

    fun getFocusStartMs(): Long = prefs.getLong(KEY_FOCUS_START_MS, 0L)

    fun getBlockedPackages(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED_PACKAGES, null)?.toSet() ?: DEFAULT_BLOCKED_PACKAGES

    fun setBlockedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, packages).apply()
    }

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingComplete() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    fun initializeDefaultsIfNeeded() {
        if (prefs.getBoolean(KEY_HAS_INITIALIZED_DEFAULTS, false)) return
        prefs.edit()
            .putStringSet(KEY_BLOCKED_PACKAGES, DEFAULT_BLOCKED_PACKAGES)
            .putBoolean(KEY_HAS_INITIALIZED_DEFAULTS, true)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "focus_store"
        private const val KEY_FOCUS_ACTIVE = "focus_mode_active"
        private const val KEY_FOCUS_START_MS = "focus_start_ms"
        private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_HAS_INITIALIZED_DEFAULTS = "has_initialized_defaults"

        val DEFAULT_BLOCKED_PACKAGES: Set<String> = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically"
        )

        val BLOCKED_APP_LABELS: Map<String, String> = mapOf(
            "com.instagram.android" to "Instagram",
            "com.google.android.youtube" to "YouTube",
            "com.zhiliaoapp.musically" to "TikTok"
        )
    }
}

fun formatDurationMmSs(totalMs: Long): String {
    val totalSeconds = max(0L, totalMs) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
