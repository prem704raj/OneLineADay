package com.onelineaday.dailydiary.billing

import com.android.billingclient.api.Purchase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingManagerTest {

    @Test
    fun recognizedProducts_containExactIds() {
        assertTrue(BillingManager.RECOGNIZED_PRODUCTS.contains("premium_monthly"))
        assertTrue(BillingManager.RECOGNIZED_PRODUCTS.contains("premium_lifetime"))
        assertEquals(2, BillingManager.RECOGNIZED_PRODUCTS.size)
    }

    @Test
    fun unrecognizedProduct_rejected() {
        assertFalse(BillingManager.RECOGNIZED_PRODUCTS.contains("fake_plan"))
        assertFalse(BillingManager.RECOGNIZED_PRODUCTS.contains("premium_yearly"))
        assertFalse(BillingManager.RECOGNIZED_PRODUCTS.contains(""))
    }

    @Test
    fun getFormattedPrice_returnsNullWhenNotLoaded() {
        assertNull(BillingManager.getFormattedPrice(BillingManager.PRODUCT_MONTHLY))
        assertNull(BillingManager.getFormattedPrice(BillingManager.PRODUCT_LIFETIME))
        assertNull(BillingManager.getFormattedPrice("unrecognized"))
    }

    @Test
    fun isEntitledPurchase_purchasedAndRecognized_qualifies() {
        val qualifiesMonthly = BillingManager.isEntitledPurchase(
            productIds = listOf("premium_monthly"),
            purchaseState = Purchase.PurchaseState.PURCHASED,
            suspended = false
        )
        assertTrue(qualifiesMonthly)

        val qualifiesLifetime = BillingManager.isEntitledPurchase(
            productIds = listOf("premium_lifetime"),
            purchaseState = Purchase.PurchaseState.PURCHASED,
            suspended = false
        )
        assertTrue(qualifiesLifetime)
    }

    @Test
    fun isEntitledPurchase_pendingDoesNotQualify() {
        val qualifiesPending = BillingManager.isEntitledPurchase(
            productIds = listOf("premium_monthly"),
            purchaseState = Purchase.PurchaseState.PENDING,
            suspended = false
        )
        assertFalse(qualifiesPending)
    }

    @Test
    fun isEntitledPurchase_unspecifiedStateDoesNotQualify() {
        val qualifiesUnspecified = BillingManager.isEntitledPurchase(
            productIds = listOf("premium_monthly"),
            purchaseState = Purchase.PurchaseState.UNSPECIFIED_STATE,
            suspended = false
        )
        assertFalse(qualifiesUnspecified)
    }

    @Test
    fun isEntitledPurchase_suspendedSubscriptionDoesNotQualify() {
        val qualifiesSuspended = BillingManager.isEntitledPurchase(
            productIds = listOf("premium_monthly"),
            purchaseState = Purchase.PurchaseState.PURCHASED,
            suspended = true
        )
        assertFalse(qualifiesSuspended)
    }

    @Test
    fun isEntitledPurchase_unrecognizedProductDoesNotQualify() {
        val qualifiesUnknown = BillingManager.isEntitledPurchase(
            productIds = listOf("unknown_tier"),
            purchaseState = Purchase.PurchaseState.PURCHASED,
            suspended = false
        )
        assertFalse(qualifiesUnknown)
    }
}
