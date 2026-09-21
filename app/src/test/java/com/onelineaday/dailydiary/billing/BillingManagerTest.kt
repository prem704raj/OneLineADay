package com.onelineaday.dailydiary.billing

import com.onelineaday.dailydiary.PremiumManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingManagerTest {

    @Test
    fun `recognized products contains monthly and lifetime only`() {
        assertEquals(2, BillingManager.RECOGNIZED_PRODUCTS.size)
        assertTrue(BillingManager.RECOGNIZED_PRODUCTS.contains("premium_monthly"))
        assertTrue(BillingManager.RECOGNIZED_PRODUCTS.contains("premium_lifetime"))
    }

    @Test
    fun `product constants match expected Play Console product IDs`() {
        assertEquals("premium_monthly", BillingManager.PRODUCT_MONTHLY)
        assertEquals("premium_lifetime", BillingManager.PRODUCT_LIFETIME)
    }

    @Test
    fun `arbitrary and malicious product IDs are not recognized`() {
        val invalidProductIds = listOf(
            "unrelated_sku",
            "free_upgrade",
            "admin_unlock",
            "",
            "premium_monthly_hack",
            "premium_yearly"
        )

        for (id in invalidProductIds) {
            assertFalse(
                "Product ID $id should not be recognized",
                BillingManager.RECOGNIZED_PRODUCTS.contains(id)
            )
        }
    }

    @Test
    fun `initial product map is empty`() {
        assertTrue(BillingManager.products.value.isEmpty())
    }

    @Test
    fun `formatted price returns null for unknown product`() {
        assertEquals(null, BillingManager.getFormattedPrice("non_existent_product"))
    }
}
