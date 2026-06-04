package com.zack.focus

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages the Google Play subscription for Focus Premium.
 *
 * Setup checklist (one-time, in Play Console):
 * 1. Create subscription product with ID [PRODUCT_ID] ("focus_premium_monthly")
 * 2. Set price to $4.99/month (or your preferred amount)
 * 3. Publish the app to at least an internal testing track before billing works
 */
class BillingManager(
    private val context: Context,
    private val onPremiumStatusChanged: (Boolean) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        } else {
            Log.w(TAG, "Purchase update: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    checkExistingSubscription()
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected — will retry on next action")
            }
        })
    }

    /** Call on every app resume to catch subscription lapses or renewals. */
    fun checkExistingSubscription() {
        if (!client.isReady) return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { _, purchases ->
            val active = purchases.any { purchase ->
                purchase.products.contains(PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            onPremiumStatusChanged(active)
        }
    }

    /** Launch the Play billing sheet from the given Activity. */
    fun launchBillingFlow(activity: Activity) {
        if (!client.isReady) {
            connect()
            return
        }

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        ) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || details.isEmpty()) {
                Log.e(TAG, "Product query failed: ${result.debugMessage}")
                return@queryProductDetailsAsync
            }

            val product = details[0]
            val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken
                ?: return@queryProductDetailsAsync

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(product)
                            .setOfferToken(offerToken)
                            .build()
                    )
                )
                .build()

            scope.launch(Dispatchers.Main) {
                client.launchBillingFlow(activity, flowParams)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        onPremiumStatusChanged(true)
        PremiumManager.grantPremium()

        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.e(TAG, "Acknowledge failed: ${result.debugMessage}")
                }
            }
        }
    }

    fun disconnect() = client.endConnection()

    companion object {
        private const val TAG = "FocusBilling"
        const val PRODUCT_ID = "focus_premium_monthly"
    }
}
