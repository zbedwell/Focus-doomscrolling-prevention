package com.zack.focus

object GatePolicy {

    fun shouldGate(packageName: String, focusStore: FocusStore): Boolean {
        if (!focusStore.isFocusModeActive()) return false
        val mode = focusStore.getBlockMode(packageName)
        if (mode == BlockMode.OFF) return false
        // FRIENDS_ONLY enforced same as FULL for MVP; Accessibility Service will differentiate later.
        return !TemporaryUnlockManager.isUnlocked(packageName)
    }

    fun isBlockedPackage(packageName: String, focusStore: FocusStore): Boolean =
        focusStore.getBlockMode(packageName) != BlockMode.OFF
}
