package com.zack.focus

/**
 * Manages premium subscription state.
 *
 * Current implementation: SharedPreferences flag (set by FocusStore).
 * Future: replace body with Google Play Billing Library subscription check.
 * Product ID to use: "focus_premium_monthly" (set up in Play Console).
 */
object PremiumManager {

    private var store: FocusStore? = null

    fun init(store: FocusStore) {
        this.store = store
    }

    fun isPremium(): Boolean = store?.isPremium() ?: false

    /** Call this when a Play Billing purchase is confirmed. */
    fun grantPremium() {
        store?.setPremium(true)
    }

    /** Call this when a subscription lapses. */
    fun revokePremium() {
        store?.setPremium(false)
    }
}
