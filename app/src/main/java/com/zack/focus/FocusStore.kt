package com.zack.focus

import android.content.Context
import java.util.Locale
import kotlin.math.max

enum class BlockMode { OFF, FULL, FRIENDS_ONLY }

class FocusStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Focus Mode ────────────────────────────────────────────────────────────

    fun isFocusModeActive(): Boolean = prefs.getBoolean(KEY_FOCUS_ACTIVE, false)

    fun setFocusModeActive(active: Boolean) {
        val edit = prefs.edit().putBoolean(KEY_FOCUS_ACTIVE, active)
        if (active) edit.putLong(KEY_FOCUS_START_MS, System.currentTimeMillis())
        edit.apply()
    }

    fun getFocusStartMs(): Long = prefs.getLong(KEY_FOCUS_START_MS, 0L)

    // ── Per-app block mode ────────────────────────────────────────────────────

    fun getBlockMode(packageName: String): BlockMode {
        val stored = prefs.getString("$KEY_BLOCK_MODE_PREFIX$packageName", null)
            ?: return if (packageName in DEFAULT_BLOCKED_PACKAGES) BlockMode.FULL else BlockMode.OFF
        return runCatching { BlockMode.valueOf(stored) }.getOrDefault(BlockMode.FULL)
    }

    fun setBlockMode(packageName: String, mode: BlockMode) {
        prefs.edit().putString("$KEY_BLOCK_MODE_PREFIX$packageName", mode.name).apply()
    }

    fun setAllBlockModes(mode: BlockMode) {
        val edit = prefs.edit()
        DEFAULT_BLOCKED_PACKAGES.forEach { pkg ->
            edit.putString("$KEY_BLOCK_MODE_PREFIX$pkg", mode.name)
        }
        edit.apply()
    }

    // Legacy accessor kept for WatcherService notification text and onboarding compat.
    fun getBlockedPackages(): Set<String> =
        DEFAULT_BLOCKED_PACKAGES.filter { getBlockMode(it) != BlockMode.OFF }.toSet()

    // ── Premium ───────────────────────────────────────────────────────────────

    fun isPremium(): Boolean = prefs.getBoolean(KEY_PREMIUM, false)

    fun setPremium(premium: Boolean) {
        prefs.edit().putBoolean(KEY_PREMIUM, premium).apply()
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingComplete() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    fun initializeDefaultsIfNeeded() {
        if (prefs.getBoolean(KEY_HAS_INITIALIZED_DEFAULTS, false)) return
        prefs.edit()
            .putBoolean(KEY_HAS_INITIALIZED_DEFAULTS, true)
            .apply()
        // BlockModes default to FULL for all three apps via getBlockMode() fallback.
    }

    companion object {
        private const val PREFS_NAME = "focus_store"
        private const val KEY_FOCUS_ACTIVE = "focus_mode_active"
        private const val KEY_FOCUS_START_MS = "focus_start_ms"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_HAS_INITIALIZED_DEFAULTS = "has_initialized_defaults"
        private const val KEY_BLOCK_MODE_PREFIX = "block_mode_"
        private const val KEY_PREMIUM = "is_premium"

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
