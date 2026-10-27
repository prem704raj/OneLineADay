package com.onelineaday.dailydiary.e2e.tier1

import com.onelineaday.dailydiary.e2e.engine.AppLockSecurityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1: Core Feature Coverage for App Lock & PIN Security Engine (F18).
 */
class Tier1AppLockSecurityFeatureTest {

    @Test
    fun `salted pin hashing formats correctly with prefix, salt and hash`() {
        val pin = "1234"
        val hash = AppLockSecurityEngine.hashPinSalted(pin)

        assertTrue("Hash must start with s1: version indicator", hash.startsWith("s1:"))
        val parts = hash.split(":")
        assertEquals("Hash must have exactly 3 parts separated by colons", 3, parts.size)
        assertEquals("Salt must be 32 hex characters (16 bytes)", 32, parts[1].length)
        assertEquals("Hash must be 64 hex characters (32 bytes SHA-256)", 64, parts[2].length)
    }

    @Test
    fun `salted pin hashing produces distinct hashes for same pin due to random salt`() {
        val pin = "4321"
        val hash1 = AppLockSecurityEngine.hashPinSalted(pin)
        val hash2 = AppLockSecurityEngine.hashPinSalted(pin)

        assertNotEquals("Different random salts must produce different hash outputs", hash1, hash2)
        assertTrue(AppLockSecurityEngine.verifyPinConstantTime(hash1, pin))
        assertTrue(AppLockSecurityEngine.verifyPinConstantTime(hash2, pin))
    }

    @Test
    fun `constant time verification rejects incorrect pin`() {
        val hash = AppLockSecurityEngine.hashPinSalted("9876")
        assertFalse(AppLockSecurityEngine.verifyPinConstantTime(hash, "9875"))
        assertFalse(AppLockSecurityEngine.verifyPinConstantTime(hash, "0000"))
        assertFalse(AppLockSecurityEngine.verifyPinConstantTime(hash, ""))
    }

    @Test
    fun `throttling allows up to 4 failed attempts before triggering lockout`() {
        val result1 = AppLockSecurityEngine.calculateThrottling(failedAttempts = 1)
        assertTrue(result1 is AppLockSecurityEngine.PinResult.Failed)
        assertEquals(4, (result1 as AppLockSecurityEngine.PinResult.Failed).remainingAttempts)

        val result4 = AppLockSecurityEngine.calculateThrottling(failedAttempts = 4)
        assertTrue(result4 is AppLockSecurityEngine.PinResult.Failed)
        assertEquals(1, (result4 as AppLockSecurityEngine.PinResult.Failed).remainingAttempts)
    }

    @Test
    fun `throttling locks out user on fifth failed attempt with thirty seconds initial lockout`() {
        val result5 = AppLockSecurityEngine.calculateThrottling(failedAttempts = 5)
        assertTrue(result5 is AppLockSecurityEngine.PinResult.LockedOut)
        assertEquals(30L, (result5 as AppLockSecurityEngine.PinResult.LockedOut).lockoutSeconds)
    }

    @Test
    fun `masking policy dictates privacy across widgets and notifications`() {
        // App Lock enabled, not authenticated -> MUST MASK
        assertTrue(AppLockSecurityEngine.shouldMaskContent(isAppLockEnabled = true, isSessionAuthenticated = false))

        // App Lock enabled, authenticated -> DO NOT MASK
        assertFalse(AppLockSecurityEngine.shouldMaskContent(isAppLockEnabled = true, isSessionAuthenticated = true))

        // App Lock disabled -> NEVER MASK
        assertFalse(AppLockSecurityEngine.shouldMaskContent(isAppLockEnabled = false, isSessionAuthenticated = false))
    }
}
