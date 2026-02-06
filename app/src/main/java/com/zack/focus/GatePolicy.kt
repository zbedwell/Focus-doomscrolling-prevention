package com.zack.focus

object GatePolicy {

    // MVP: hardcoded list
    val blockedPackages: Set<String> = setOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically" // TikTok
    )

    private val lastUnlockMs = mutableMapOf<String, Long>()
    private const val COOLDOWN_MS = 120_000L

    fun shouldGate(packageName: String): Boolean {
        if (!blockedPackages.contains(packageName)) return false
        val last = lastUnlockMs[packageName] ?: 0L
        return (System.currentTimeMillis() - last) > COOLDOWN_MS
    }

    fun recordUnlocked(packageName: String) {
        lastUnlockMs[packageName] = System.currentTimeMillis()
    }
}
