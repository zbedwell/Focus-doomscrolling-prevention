package com.zack.focus

data class TemporaryUnlock(
    val packageName: String,
    val expiresAtMillis: Long
)

object TemporaryUnlockManager {

    // Accessed from multiple threads (main thread + watcher coroutine on Dispatchers.Default)
    private val unlocks = mutableListOf<TemporaryUnlock>()

    private const val UNLOCK_DURATION_MS = 5 * 60 * 1000L

    @Synchronized
    fun grant(packageName: String, nowMs: Long = System.currentTimeMillis()) {
        unlocks.removeAll { it.packageName == packageName }
        unlocks.add(TemporaryUnlock(packageName, nowMs + UNLOCK_DURATION_MS))
    }

    @Synchronized
    fun isUnlocked(packageName: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        pruneExpired(nowMs)
        return unlocks.any { it.packageName == packageName }
    }

    @Synchronized
    fun remainingMs(packageName: String, nowMs: Long = System.currentTimeMillis()): Long {
        pruneExpired(nowMs)
        return unlocks.firstOrNull { it.packageName == packageName }
            ?.let { maxOf(0L, it.expiresAtMillis - nowMs) } ?: 0L
    }

    private fun pruneExpired(nowMs: Long) {
        unlocks.removeAll { it.expiresAtMillis <= nowMs }
    }
}
