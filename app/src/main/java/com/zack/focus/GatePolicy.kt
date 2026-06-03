package com.zack.focus

object GatePolicy {

    fun isBlockedPackage(packageName: String, focusStore: FocusStore): Boolean =
        focusStore.getBlockedPackages().contains(packageName)

    fun shouldGate(packageName: String, focusStore: FocusStore): Boolean {
        if (!focusStore.isFocusModeActive()) return false
        if (!isBlockedPackage(packageName, focusStore)) return false
        return !TemporaryUnlockManager.isUnlocked(packageName)
    }
}
