package com.onelineaday.dailydiary.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.billingclient.api.*
import com.onelineaday.dailydiary.BuildConfig
import com.onelineaday.dailydiary.PremiumManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object BillingManager : PurchasesUpdatedListener {

    private const val TAG = "BillingManager"
    private var billingClient: BillingClient? = null
    private var appContext: Context? = null

    // Recognized product IDs (must match Google Play Console)
    const val PRODUCT_MONTHLY = "premium_monthly"
    const val PRODUCT_LIFETIME = "premium_lifetime"
    val RECOGNIZED_PRODUCTS = setOf(PRODUCT_MONTHLY, PRODUCT_LIFETIME)

    // Reactive state for UI
    private val _products = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val products: StateFlow<Map<String, ProductDetails>> = _products.asStateFlow()

    private val _isLoadingProducts = MutableStateFlow(false)
    val isLoadingProducts: StateFlow<Boolean> = _isLoadingProducts.asStateFlow()

    private val _catalogMessage = MutableStateFlow<String?>(null)
    val catalogMessage: StateFlow<String?> = _catalogMessage.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        if (billingClient == null) {
            billingClient = BillingClient.newBuilder(context.applicationContext)
                .setListener(this)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder()
                        .enableOneTimeProducts()
                        .enablePrepaidPlans()
                        .build()
                )
                .enableAutoServiceReconnection()
                .build()
        }

        connect()
    }

    fun connect(onConnected: (() -> Unit)? = null) {
        val client = billingClient ?: return
        if (client.isReady) {
            refreshProducts()
            checkPurchases()
            onConnected?.invoke()
            return
        }

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected successfully")
                    refreshProducts()
                    checkPurchases()
                    onConnected?.invoke()
                } else {
                    Log.e(
                        TAG,
                        "Billing setup failed: ${billingResult.responseCode} ${billingResult.debugMessage}"
                    )
                    _catalogMessage.value = friendlyBillingError(billingResult)
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                // PBL 9 auto-reconnects because enableAutoServiceReconnection() is enabled
            }
        })
    }

    fun refreshProducts() {
        val client = billingClient
        if (client == null || !client.isReady) {
            connect()
            return
        }

        _isLoadingProducts.value = true
        _catalogMessage.value = null

        val newProductsMap = HashMap<String, ProductDetails>(_products.value)
        val queriesCompleted = AtomicInteger(0)
        val errors = mutableListOf<String>()

        fun onQueryDone(product: ProductDetails?, error: String?) {
            if (product != null) {
                synchronized(newProductsMap) {
                    newProductsMap[product.productId] = product
                }
            }
            if (!error.isNullOrBlank()) {
                synchronized(errors) {
                    errors.add(error)
                }
            }
            if (queriesCompleted.incrementAndGet() == 2) {
                _products.value = HashMap(newProductsMap)
                _isLoadingProducts.value = false
                if (newProductsMap.isEmpty()) {
                    val combined = errors.distinct().joinToString(" ")
                    _catalogMessage.value = combined.ifBlank {
                        "Google Play products are not available. Please verify products are active in Google Play Console."
                    }
                } else {
                    _catalogMessage.value = null
                }
            }
        }

        // Query Subscription (premium_monthly)
        querySingleProduct(PRODUCT_MONTHLY, BillingClient.ProductType.SUBS) { prod, err ->
            onQueryDone(prod, err)
        }

        // Query In-App One-Time (premium_lifetime)
        querySingleProduct(PRODUCT_LIFETIME, BillingClient.ProductType.INAPP) { prod, err ->
            onQueryDone(prod, err)
        }
    }

    private fun querySingleProduct(
        productId: String,
        productType: String,
        callback: (ProductDetails?, String?) -> Unit
    ) {
        val client = billingClient
        if (client == null || !client.isReady) {
            callback(null, "Billing service not ready")
            return
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(productType)
                        .build()
                )
            )
            .build()

        client.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val list = queryProductDetailsResult.productDetailsList
                val product = list.firstOrNull { it.productId == productId }
                if (product != null) {
                    callback(product, null)
                } else {
                    val unfetched = queryProductDetailsResult.unfetchedProductList.firstOrNull { it.productId == productId }
                    val msg = if (unfetched != null) {
                        "Product '$productId' is unavailable in Google Play (status ${unfetched.statusCode})."
                    } else {
                        "Product '$productId' was not found in the Play Console catalog."
                    }
                    Log.w(TAG, msg)
                    callback(null, msg)
                }
            } else {
                val err = friendlyBillingError(billingResult)
                Log.w(TAG, "Query product details failed for $productId: $err")
                callback(null, err)
            }
        }
    }

    fun getFormattedPrice(productId: String): String? {
        val details = _products.value[productId] ?: return null
        return if (details.productType == BillingClient.ProductType.SUBS) {
            details.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
        } else {
            details.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    fun launchBillingFlow(
        activity: Activity,
        productId: String,
        isSubscription: Boolean,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (productId !in RECOGNIZED_PRODUCTS) {
            val msg = "Attempted to launch billing flow for unrecognized product: $productId"
            Log.e(TAG, msg)
            if (onError != null) onError(msg) else Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            return
        }

        val client = billingClient
        val productDetails = _products.value[productId]

        // Fallback in DEBUG mode if Play Console products have not propagated or testing locally
        if (productDetails == null) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Product '$productId' not returned by Google Play in DEBUG mode. Simulating purchase for testing.")
                appContext?.let { ctx ->
                    PremiumManager.setPremium(ctx, true, testMode = true)
                }
                Toast.makeText(
                    activity,
                    "DEBUG TEST MODE: Simulated purchase successful! Premium activated.",
                    Toast.LENGTH_LONG
                ).show()
                onSuccess?.invoke()
                return
            }

            val msg = "Product '$productId' is not ready in Google Play. Please ensure it is active in Play Console."
            Log.w(TAG, msg)
            if (onError != null) onError(msg) else Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            refreshProducts()
            return
        }

        if (client == null || !client.isReady) {
            val msg = "Google Play Billing service is connecting. Please try again."
            Log.w(TAG, msg)
            if (onError != null) onError(msg) else Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            connect()
            return
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (isSubscription) {
            // Find the best active base plan offer token
            val offerToken = productDetails.subscriptionOfferDetails
                ?.firstOrNull { it.offerToken.isNotEmpty() }
                ?.offerToken
            if (offerToken.isNullOrEmpty()) {
                val msg = "No active subscription offer or base plan found for '$productId' in Google Play."
                Log.e(TAG, msg)
                if (onError != null) onError(msg) else Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                return
            }
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
            .build()

        val billingResult = client.launchBillingFlow(activity, billingFlowParams)
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            val msg = friendlyBillingError(billingResult)
            Log.e(TAG, "Failed to launch billing flow: $msg")
            if (onError != null) onError(msg) else Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun checkPurchases(onComplete: ((Boolean) -> Unit)? = null) {
        val client = billingClient
        if (client == null || !client.isReady) {
            onComplete?.invoke(PremiumManager.isPremium.value)
            return
        }

        val foundActivePurchase = AtomicBoolean(false)
        val checksCompleted = AtomicInteger(0)
        val hasQueryError = AtomicBoolean(false)

        fun onCheckDone() {
            if (checksCompleted.incrementAndGet() == 2) {
                appContext?.let { ctx ->
                    if (foundActivePurchase.get()) {
                        if (!PremiumManager.isPremium.value) {
                            Log.d(TAG, "Active purchase confirmed by Google Play. Enabling premium.")
                            PremiumManager.setPremium(ctx, true)
                        }
                    } else {
                        // DO NOT revoke premium if in debug/test mode or if one of the queries failed
                        if (!PremiumManager.isDebugOrTestMode() && !hasQueryError.get()) {
                            if (PremiumManager.isPremium.value) {
                                Log.d(TAG, "No active purchases found. Revoking premium.")
                                PremiumManager.setPremium(ctx, false)
                            }
                        }
                    }
                }
                onComplete?.invoke(PremiumManager.isPremium.value)
            }
        }

        // Check Subscriptions
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.any { p -> p in RECOGNIZED_PRODUCTS } }) {
                    foundActivePurchase.set(true)
                }
                handlePurchases(purchases)
            } else {
                hasQueryError.set(true)
            }
            onCheckDone()
        }

        // Check One-Time Purchases
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.any { p -> p in RECOGNIZED_PRODUCTS } }) {
                    foundActivePurchase.set(true)
                }
                handlePurchases(purchases)
            } else {
                hasQueryError.set(true)
            }
            onCheckDone()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled the purchase flow")
        } else {
            val errorMsg = friendlyBillingError(billingResult)
            Log.e(
                TAG,
                "Purchase update error: ${billingResult.responseCode} ${billingResult.debugMessage}"
            )
            appContext?.let { ctx ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(ctx, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val client = billingClient ?: return
        for (purchase in purchases) {
            val hasRecognizedProduct = purchase.products.any { it in RECOGNIZED_PRODUCTS }
            if (hasRecognizedProduct && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                appContext?.let { ctx ->
                    if (!PremiumManager.isPremium.value) {
                        Log.d(TAG, "Valid purchase confirmed. Granting premium.")
                        PremiumManager.setPremium(ctx, true)
                    }
                }
                if (!purchase.isAcknowledged) {
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    client.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
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

    fun restorePurchases(activity: Activity) {
        val client = billingClient
        if (client == null || !client.isReady) {
            Toast.makeText(activity, "Connecting to Google Play...", Toast.LENGTH_SHORT).show()
            connect {
                restorePurchases(activity)
            }
            return
        }

        Toast.makeText(activity, "Checking for previous purchases...", Toast.LENGTH_SHORT).show()
        checkPurchases { isPremiumNow ->
            activity.runOnUiThread {
                if (isPremiumNow) {
                    Toast.makeText(activity, "Premium restored successfully! All features are unlocked.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(activity, "No active Premium purchase found for this Google account.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun friendlyBillingError(result: BillingResult): String {
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                "The product is not available in Google Play. Please verify the product ID and test account in Google Play Console."
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                "Google Play Billing is unavailable on this device or account."
            BillingClient.BillingResponseCode.NETWORK_ERROR ->
                "Network error. Please check your internet connection and try again."
            BillingClient.BillingResponseCode.USER_CANCELED ->
                "Purchase was cancelled."
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                "You already own this item. Tap 'Restore Purchases' in Settings."
            BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
                "Google Play configuration error: ${result.debugMessage}"
            BillingClient.BillingResponseCode.ERROR ->
                "Google Play encountered an internal error. Please try again later."
            else ->
                "Billing response (${result.responseCode}): ${result.debugMessage.ifBlank { "Unknown error" }}"
        }
    }
}
