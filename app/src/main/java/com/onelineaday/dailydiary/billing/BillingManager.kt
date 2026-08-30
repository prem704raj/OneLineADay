package com.onelineaday.dailydiary.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.*
import com.onelineaday.dailydiary.PremiumManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object BillingManager : PurchasesUpdatedListener {

    private const val TAG = "BillingManager"
    private lateinit var billingClient: BillingClient
    private var isConnected = false
    private var appContext: Context? = null
    private var retryCount = 0
    private const val MAX_RETRIES = 3
    
    // Replace these with your actual product IDs from the Google Play Console
    const val PRODUCT_MONTHLY = "premium_monthly"
    const val PRODUCT_LIFETIME = "premium_lifetime"

    fun init(context: Context) {
        appContext = context.applicationContext
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases()
            .build()
            
        connectToBilling()
    }

    private fun connectToBilling() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected successfully")
                    isConnected = true
                    retryCount = 0
                    checkPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    isConnected = false
                }
            }
            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                isConnected = false
                // Retry connection with limit
                if (retryCount < MAX_RETRIES) {
                    retryCount++
                    Log.d(TAG, "Retrying billing connection (attempt $retryCount/$MAX_RETRIES)")
                    connectToBilling()
                }
            }
        })
    }

    fun checkPurchases() {
        var foundActivePurchase = false
        var checksCompleted = 0
        
        // Check subscriptions
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                    foundActivePurchase = true
                }
                handlePurchases(purchases)
            }
            checksCompleted++
            if (checksCompleted == 2 && !foundActivePurchase) {
                // No active purchases found in either SUBS or INAPP — revoke premium
                appContext?.let { ctx ->
                    if (PremiumManager.isPremium.value) {
                        Log.d(TAG, "No active purchases found. Revoking premium.")
                        PremiumManager.setPremium(ctx, false)
                    }
                }
            }
        }
        
        // Check one-time purchases
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                    foundActivePurchase = true
                }
                handlePurchases(purchases)
            }
            checksCompleted++
            if (checksCompleted == 2 && !foundActivePurchase) {
                // No active purchases found in either SUBS or INAPP — revoke premium
                appContext?.let { ctx ->
                    if (PremiumManager.isPremium.value) {
                        Log.d(TAG, "No active purchases found. Revoking premium.")
                        PremiumManager.setPremium(ctx, false)
                    }
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled the purchase flow")
        } else {
            Log.e(TAG, "Purchase update error: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                // Grant premium
                appContext?.let { ctx ->
                    if (!PremiumManager.isPremium.value) {
                        PremiumManager.setPremium(ctx, true)
                    }
                }
                // Acknowledge the purchase if not yet acknowledged
                if (!purchase.isAcknowledged) {
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "Purchase acknowledged successfully")
                        } else {
                            Log.e(TAG, "Failed to acknowledge purchase: ${billingResult.debugMessage}")
                        }
                    }
                }
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productId: String, isSubscription: Boolean) {
        if (!isConnected) {
            Toast.makeText(activity, "Billing service unavailable. Please try again later.", Toast.LENGTH_SHORT).show()
            // Try to reconnect
            retryCount = 0
            connectToBilling()
            return
        }

        val productType = if (isSubscription) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP

        val queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(productType)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(queryProductDetailsParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                
                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .apply {
                            if (isSubscription) {
                                productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken?.let { token ->
                                    setOfferToken(token)
                                }
                            }
                        }
                        .build()
                )

                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()

                // Launch the billing flow
                billingClient.launchBillingFlow(activity, billingFlowParams)
            } else {
                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "This product is not available yet. Please try again later.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Call this to restore purchases, e.g. from a "Restore Purchases" button in Settings.
     */
    fun restorePurchases(activity: Activity) {
        if (!isConnected) {
            Toast.makeText(activity, "Billing service unavailable. Please try again later.", Toast.LENGTH_SHORT).show()
            retryCount = 0
            connectToBilling()
            return
        }
        
        checkPurchases()
        Toast.makeText(activity, "Checking for previous purchases...", Toast.LENGTH_SHORT).show()
    }
}
