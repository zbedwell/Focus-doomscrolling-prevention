package com.zack.focus

object GatePolicy {

    private val lastUnlockMs = mutableMapOf<String, Long>()
    private const val COOLDOWN_MS = 120_000L

    fun isBlocked(packageName: String, focusStore: FocusStore): Boolean {
        return focusStore.getBlockedPackages().contains(packageName)
    }

    fun shouldGate(
        packageName: String,
        focusStore: FocusStore,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isBlocked(packageName, focusStore)) return false
        if (focusStore.isSessionActive(nowMs)) return true

        val last = lastUnlockMs[packageName] ?: 0L
        return (nowMs - last) > COOLDOWN_MS
    }

    fun recordUnlocked(packageName: String, nowMs: Long = System.currentTimeMillis()) {
        lastUnlockMs[packageName] = nowMs
    }
}
