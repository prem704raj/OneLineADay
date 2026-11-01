package com.onelineaday.dailydiary.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test Double tracking window flag operations and bitmask states.
 */
class FakeWindowMutator : WindowFlagMutator {
    var currentFlags: Int = 0
        private set
    var setFlagsCallCount: Int = 0
        private set
    var clearFlagsCallCount: Int = 0
        private set

    override fun setFlags(flags: Int, mask: Int) {
        currentFlags = (currentFlags and mask.inv()) or (flags and mask)
        setFlagsCallCount++
    }

    override fun clearFlags(flags: Int) {
        currentFlags = currentFlags and flags.inv()
        clearFlagsCallCount++
    }

    fun hasFlag(flag: Int): Boolean = (currentFlags and flag) != 0

    fun resetCounts() {
        setFlagsCallCount = 0
        clearFlagsCallCount = 0
    }
}

/**
 * Automated tests for Dynamic FLAG_SECURE Window Privacy.
 */
class WindowPrivacySecurityTest {

    private val fakeWindow = FakeWindowMutator()
    private var recentsScreenshotEnabled: Boolean? = null
    private val controller = WindowPrivacyController(fakeWindow) { enabled ->
        recentsScreenshotEnabled = enabled
    }
    private val flagSecure = android.view.WindowManager.LayoutParams.FLAG_SECURE // 0x2000 = 8192

    @Test
    fun `flagSecure is applied when app lock is enabled and session is locked`() {
        controller.updateWindowPrivacy(isLocked = true, isAppLockEnabled = true)
        assertTrue("FLAG_SECURE must be applied when locked and app lock enabled", fakeWindow.hasFlag(flagSecure))
        assertTrue(controller.isFlagSecureApplied)
        assertEquals(1, fakeWindow.setFlagsCallCount)
        assertEquals(false, recentsScreenshotEnabled)
    }

    @Test
    fun `flagSecure is cleared when session is unlocked`() {
        // Given locked state
        controller.updateWindowPrivacy(isLocked = true, isAppLockEnabled = true)
        assertTrue(fakeWindow.hasFlag(flagSecure))

        // When unlocked
        controller.updateWindowPrivacy(isLocked = false, isAppLockEnabled = true)
        assertFalse("FLAG_SECURE must be removed when session is unlocked", fakeWindow.hasFlag(flagSecure))
        assertFalse(controller.isFlagSecureApplied)
        assertEquals(1, fakeWindow.clearFlagsCallCount)
        assertEquals(true, recentsScreenshotEnabled)
    }

    @Test
    fun `flagSecure is never applied when app lock is disabled even if marked locked`() {
        controller.updateWindowPrivacy(isLocked = true, isAppLockEnabled = false)
        assertFalse("FLAG_SECURE must not be set when app lock is disabled", fakeWindow.hasFlag(flagSecure))
        assertFalse(controller.isFlagSecureApplied)
        assertEquals(true, recentsScreenshotEnabled)
    }

    @Test
    fun `onPause applies flagSecure when app lock is enabled regardless of foreground unlock state`() {
        // App is unlocked in foreground
        controller.updateWindowPrivacy(isLocked = false, isAppLockEnabled = true)
        assertFalse(fakeWindow.hasFlag(flagSecure))

        // User switches away or backgrounds app -> onPause
        controller.onPause(isAppLockEnabled = true)
        assertTrue("FLAG_SECURE must be applied on pause to mask recent apps snapshot", fakeWindow.hasFlag(flagSecure))
        assertTrue(controller.isFlagSecureApplied)
        assertEquals(false, recentsScreenshotEnabled)
    }

    @Test
    fun `onPause does not apply flagSecure when app lock is disabled`() {
        controller.onPause(isAppLockEnabled = false)
        assertFalse("FLAG_SECURE must not be applied on pause if app lock is disabled", fakeWindow.hasFlag(flagSecure))
        assertFalse(controller.isFlagSecureApplied)
    }

    @Test
    fun `onResume clears flagSecure if session remains unlocked within grace window`() {
        // Backgrounded state has flag applied
        controller.onPause(isAppLockEnabled = true)
        assertTrue(fakeWindow.hasFlag(flagSecure))

        // Resumed with valid unlocked session (< 30s)
        controller.onResume(isAppLockEnabled = true, isSessionUnlocked = true)
        assertFalse("FLAG_SECURE must be cleared on resume when session is still unlocked", fakeWindow.hasFlag(flagSecure))
        assertFalse(controller.isFlagSecureApplied)
        assertEquals(true, recentsScreenshotEnabled)
    }

    @Test
    fun `onResume retains flagSecure if session timed out and is locked`() {
        // Backgrounded state has flag applied
        controller.onPause(isAppLockEnabled = true)
        assertTrue(fakeWindow.hasFlag(flagSecure))

        // Resumed after timeout (> 30s) -> locked
        controller.onResume(isAppLockEnabled = true, isSessionUnlocked = false)
        assertTrue("FLAG_SECURE must remain active on resume if session is locked", fakeWindow.hasFlag(flagSecure))
        assertTrue(controller.isFlagSecureApplied)
        assertEquals(false, recentsScreenshotEnabled)
    }

    @Test
    fun `complete activity lifecycle sequence maintains correct window privacy state`() {
        // 1. Initial Launch: App Lock enabled, locked
        controller.updateWindowPrivacy(isLocked = true, isAppLockEnabled = true)
        assertTrue("Step 1: Launch must be secure", fakeWindow.hasFlag(flagSecure))

        // 2. User unlocks
        controller.updateWindowPrivacy(isLocked = false, isAppLockEnabled = true)
        assertFalse("Step 2: Unlocked must clear flag", fakeWindow.hasFlag(flagSecure))

        // 3. App paused / backgrounded
        controller.onPause(isAppLockEnabled = true)
        assertTrue("Step 3: Backgrounded must mask recents", fakeWindow.hasFlag(flagSecure))

        // 4. App resumed within 30s (still unlocked)
        controller.onResume(isAppLockEnabled = true, isSessionUnlocked = true)
        assertFalse("Step 4: Quick resume must reveal diary", fakeWindow.hasFlag(flagSecure))

        // 5. App paused again
        controller.onPause(isAppLockEnabled = true)
        assertTrue("Step 5: Backgrounded again must mask", fakeWindow.hasFlag(flagSecure))

        // 6. App resumed after 30s (session expired)
        controller.onResume(isAppLockEnabled = true, isSessionUnlocked = false)
        assertTrue("Step 6: Expired resume must stay secure", fakeWindow.hasFlag(flagSecure))
    }

    @Test
    fun `disabling app lock in settings immediately purges flagSecure`() {
        // App is locked
        controller.updateWindowPrivacy(isLocked = true, isAppLockEnabled = true)
        assertTrue(fakeWindow.hasFlag(flagSecure))

        // User turns off app lock
        controller.updateWindowPrivacy(isLocked = true, isAppLockEnabled = false)
        assertFalse("Disabling app lock must clear window flag", fakeWindow.hasFlag(flagSecure))
        assertFalse(controller.isFlagSecureApplied)
    }

    @Test
    fun `bitmask arithmetic precision conforms to Android WindowManager specifications`() {
        assertEquals("WindowManager.LayoutParams.FLAG_SECURE constant must equal 0x2000 (8192)", 8192, flagSecure)

        // Test composite flag preservation (other window flags must not be corrupted)
        val customFlag = 0x00000400 // FLAG_FULLSCREEN (1024)
        fakeWindow.setFlags(customFlag, customFlag)
        assertTrue(fakeWindow.hasFlag(customFlag))

        // Apply secure flag
        controller.updateWindowPrivacy(isLocked = true, isAppLockEnabled = true)
        assertTrue(fakeWindow.hasFlag(flagSecure))
        assertTrue("Preserves preexisting window flags", fakeWindow.hasFlag(customFlag))

        // Clear secure flag
        controller.updateWindowPrivacy(isLocked = false, isAppLockEnabled = true)
        assertFalse(fakeWindow.hasFlag(flagSecure))
        assertTrue("Preserves preexisting window flags when secure flag is cleared", fakeWindow.hasFlag(customFlag))
    }

    @Test
    fun `truth table exhaustively validates all 8 states in WindowPrivacyEngine`() {
        // E=0, U=0, F=0 -> M=0
        assertFalse(WindowPrivacyEngine.shouldMaskWindow(isAppLockEnabled = false, isSessionUnlocked = false, isForeground = false))
        assertTrue(WindowPrivacyEngine.isRecentsScreenshotEnabled(isAppLockEnabled = false, isSessionUnlocked = false, isForeground = false))

        // E=0, U=0, F=1 -> M=0
        assertFalse(WindowPrivacyEngine.shouldMaskWindow(isAppLockEnabled = false, isSessionUnlocked = false, isForeground = true))

        // E=0, U=1, F=0 -> M=0
        assertFalse(WindowPrivacyEngine.shouldMaskWindow(isAppLockEnabled = false, isSessionUnlocked = true, isForeground = false))

        // E=0, U=1, F=1 -> M=0
        assertFalse(WindowPrivacyEngine.shouldMaskWindow(isAppLockEnabled = false, isSessionUnlocked = true, isForeground = true))

        // E=1, U=0, F=0 -> M=1
        assertTrue(WindowPrivacyEngine.shouldMaskWindow(isAppLockEnabled = true, isSessionUnlocked = false, isForeground = false))
        assertFalse(WindowPrivacyEngine.isRecentsScreenshotEnabled(isAppLockEnabled = true, isSessionUnlocked = false, isForeground = false))

        // E=1, U=0, F=1 -> M=1
        assertTrue(WindowPrivacyEngine.shouldMaskWindow(isAppLockEnabled = true, isSessionUnlocked = false, isForeground = true))

        // E=1, U=1, F=0 -> M=1 (Backgrounded while unlocked -> must mask!)
        assertTrue(WindowPrivacyEngine.shouldMaskWindow(isAppLockEnabled = true, isSessionUnlocked = true, isForeground = false))
        assertFalse(WindowPrivacyEngine.isRecentsScreenshotEnabled(isAppLockEnabled = true, isSessionUnlocked = true, isForeground = false))

        // E=1, U=1, F=1 -> M=0 (Active in foreground while unlocked -> unmasked!)
        assertFalse(WindowPrivacyEngine.shouldMaskWindow(isAppLockEnabled = true, isSessionUnlocked = true, isForeground = true))
        assertTrue(WindowPrivacyEngine.isRecentsScreenshotEnabled(isAppLockEnabled = true, isSessionUnlocked = true, isForeground = true))
    }
}
