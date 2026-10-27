package com.onelineaday.dailydiary.e2e.tier2

import com.onelineaday.dailydiary.backup.BackupArchiveManager
import com.onelineaday.dailydiary.data.Mood
import com.onelineaday.dailydiary.data.StreakCalculator
import com.onelineaday.dailydiary.e2e.engine.AppLockSecurityEngine
import com.onelineaday.dailydiary.e2e.engine.EncryptedBackupHeader
import com.onelineaday.dailydiary.e2e.engine.LeapDayFlashbackEngine
import com.onelineaday.dailydiary.mosaic.MoodMosaicEngine
import com.onelineaday.dailydiary.mosaic.PixelCell
import com.onelineaday.dailydiary.e2e.engine.ReferenceBackupCryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Tier 2: Boundary, Corner Case & Adversarial Hardening Tests.
 * Tests empty inputs, bit-flips, corrupt data, leap centuries, buffer extremes, and traversal attempts.
 */
class Tier2BoundaryAndCornerTest {

    private val engine = ReferenceBackupCryptoEngine(iterations = 1_000)

    // ==========================================
    // 1. Cryptographic Boundary & Adversarial Tests
    // ==========================================

    @Test
    fun `empty passphrase is rejected with failure`() {
        val out = ByteArrayOutputStream()
        val result = engine.encryptStream(
            passphrase = charArrayOf(),
            plainInput = ByteArrayInputStream("Hello".toByteArray()),
            cipherOutput = out
        )
        assertTrue("Empty passphrase must fail", result.isFailure)
    }

    @Test
    fun `incorrect passphrase fails decryption cleanly without throwing unhandled exceptions`() {
        val correctPass = "CorrectPassphrase2026!".toCharArray()
        val wrongPass = "WrongPassphrase2026!".toCharArray()

        val cipherOut = ByteArrayOutputStream()
        engine.encryptStream(
            passphrase = correctPass,
            plainInput = ByteArrayInputStream("Sensitive journal content".toByteArray()),
            cipherOutput = cipherOut
        )

        val restoredOut = ByteArrayOutputStream()
        val decryptResult = engine.decryptStream(
            passphrase = wrongPass,
            cipherInput = ByteArrayInputStream(cipherOut.toByteArray()),
            plainOutput = restoredOut
        )

        assertTrue("Decryption with wrong passphrase must fail", decryptResult.isFailure)
        assertEquals("Output stream must remain empty on failed decrypt", 0, restoredOut.size())
    }

    @Test
    fun `single bit flip in ciphertext causes authentication tag failure`() {
        val pass = "IntegrityPassphrase2026".toCharArray()
        val plain = "Every single character in personal memory must be bit-level authentic."

        val cipherOut = ByteArrayOutputStream()
        engine.encryptStream(
            passphrase = pass,
            plainInput = ByteArrayInputStream(plain.toByteArray()),
            cipherOutput = cipherOut
        )

        val bytes = cipherOut.toByteArray()
        // Flip a bit in the ciphertext portion (after the 40-byte header)
        bytes[45] = (bytes[45].toInt() xor 0x01).toByte()

        val restoredOut = ByteArrayOutputStream()
        val result = engine.decryptStream(
            passphrase = pass,
            cipherInput = ByteArrayInputStream(bytes),
            plainOutput = restoredOut
        )

        assertTrue("Tampered ciphertext must fail authentication", result.isFailure)
    }

    @Test
    fun `single bit flip in authentication tag causes authentication failure`() {
        val pass = "TagIntegrityPassphrase2026".toCharArray()
        val plain = "Tag tampering test payload"

        val cipherOut = ByteArrayOutputStream()
        engine.encryptStream(
            passphrase = pass,
            plainInput = ByteArrayInputStream(plain.toByteArray()),
            cipherOutput = cipherOut
        )

        val bytes = cipherOut.toByteArray()
        // Flip the very last byte of the stream (which is part of the 16-byte GCM tag)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()

        val restoredOut = ByteArrayOutputStream()
        val result = engine.decryptStream(
            passphrase = pass,
            cipherInput = ByteArrayInputStream(bytes),
            plainOutput = restoredOut
        )

        assertTrue("Tampered GCM tag must fail authentication", result.isFailure)
    }

    @Test
    fun `corrupted magic bytes in header rejected during deserialize`() {
        val header = EncryptedBackupHeader(
            magic = byteArrayOf('B'.code.toByte(), 'A'.code.toByte(), 'D'.code.toByte(), '!'.code.toByte()),
            version = 1,
            kdfSalt = ByteArray(16),
            kdfIterations = 120_000,
            nonce = ByteArray(12)
        )
        val serialized = EncryptedBackupHeader.serialize(header)

        try {
            EncryptedBackupHeader.deserialize(serialized)
            fail("Expected IllegalArgumentException for corrupted magic")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid magic bytes"))
        }
    }

    @Test
    fun `unsupported container version rejected during deserialize`() {
        val header = EncryptedBackupHeader(
            magic = EncryptedBackupHeader.EXPECTED_MAGIC,
            version = 99, // Future/unsupported version
            kdfSalt = ByteArray(16),
            kdfIterations = 120_000,
            nonce = ByteArray(12)
        )
        val serialized = EncryptedBackupHeader.serialize(header)

        try {
            EncryptedBackupHeader.deserialize(serialized)
            fail("Expected IllegalArgumentException for unsupported version")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Unsupported container version"))
        }
    }

    @Test
    fun `insufficient KDF iterations rejected during deserialize`() {
        val header = EncryptedBackupHeader(
            magic = EncryptedBackupHeader.EXPECTED_MAGIC,
            version = 1,
            kdfSalt = ByteArray(16),
            kdfIterations = 500, // Too low; minimum is 1,000
            nonce = ByteArray(12)
        )
        val serialized = EncryptedBackupHeader.serialize(header)

        try {
            EncryptedBackupHeader.deserialize(serialized)
            fail("Expected IllegalArgumentException for weak KDF iterations")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Insufficient KDF iterations"))
        }
    }

    @Test
    fun `truncated header less than 40 bytes rejected`() {
        val shortBytes = ByteArray(25) { 0x4F }
        try {
            EncryptedBackupHeader.deserialize(shortBytes)
            fail("Expected IllegalArgumentException for truncated header")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Header truncated"))
        }
    }

    @Test
    fun `zero byte payload encrypts and decrypts cleanly with exact 56 byte output`() {
        val pass = "ZeroBytePass2026".toCharArray()
        val cipherOut = ByteArrayOutputStream()
        val encryptResult = engine.encryptStream(
            passphrase = pass,
            plainInput = ByteArrayInputStream(ByteArray(0)),
            cipherOutput = cipherOut
        )
        assertTrue(encryptResult.isSuccess)

        // 40-byte header + 16-byte GCM tag = 56 bytes
        val cipherBytes = cipherOut.toByteArray()
        assertEquals(56, cipherBytes.size)

        val restoredOut = ByteArrayOutputStream()
        val decryptResult = engine.decryptStream(
            passphrase = pass,
            cipherInput = ByteArrayInputStream(cipherBytes),
            plainOutput = restoredOut
        )
        assertTrue(decryptResult.isSuccess)
        assertEquals(0, restoredOut.size())
    }

    // ==========================================
    // 2. Calendar & Leap Century Boundary Tests
    // ==========================================

    @Test
    fun `leap century boundary rules correctly evaluate years 1600 through 2400`() {
        // Gregorian rules: Divisible by 4 is leap, UNLESS divisible by 100, EXCEPT if divisible by 400
        assertTrue("Year 1600 is divisible by 400 -> leap", LeapDayFlashbackEngine.isLeapYear(1600))
        assertFalse("Year 1700 is divisible by 100 but not 400 -> common", LeapDayFlashbackEngine.isLeapYear(1700))
        assertFalse("Year 1800 is divisible by 100 but not 400 -> common", LeapDayFlashbackEngine.isLeapYear(1800))
        assertFalse("Year 1900 is divisible by 100 but not 400 -> common", LeapDayFlashbackEngine.isLeapYear(1900))
        assertTrue("Year 2000 is divisible by 400 -> leap", LeapDayFlashbackEngine.isLeapYear(2000))
        assertFalse("Year 2100 is divisible by 100 but not 400 -> common", LeapDayFlashbackEngine.isLeapYear(2100))
        assertTrue("Year 2400 is divisible by 400 -> leap", LeapDayFlashbackEngine.isLeapYear(2400))
    }

    @Test
    fun `streak continuity across leap year transition february 28 to march 1`() {
        val feb28 = LocalDate.of(2024, 2, 28)
        val feb29 = LocalDate.of(2024, 2, 29)
        val mar1 = LocalDate.of(2024, 3, 1)

        val entries = setOf(feb28, feb29, mar1)
        val streak = StreakCalculator.current(entries, today = mar1)
        assertEquals(3, streak)

        // If Feb 29 was missed without freeze in leap year, streak breaks on Mar 1
        val brokenEntries = setOf(feb28, mar1)
        val brokenStreak = StreakCalculator.current(brokenEntries, today = mar1)
        assertEquals(1, brokenStreak) // Only Mar 1 counts
    }

    // ==========================================
    // 3. Streak Freezes & Boundary Resilience
    // ==========================================

    @Test
    fun `ten consecutive frozen dates bridge ten day vacation without count inflation`() {
        val today = LocalDate.of(2026, 8, 15)
        // Logged today, and logged 11 days ago
        val pastDate = today.minusDays(11)
        val entries = setOf(today, pastDate)

        // 10 consecutive missed days all frozen
        val frozen = (1..10).map { today.minusDays(it.toLong()) }.toSet()

        val currentStreak = StreakCalculator.current(
            entryDates = entries,
            today = today,
            frozenDates = frozen
        )

        // 2 real entries; continuity preserved across 10-day gap
        assertEquals(2, currentStreak)
    }

    @Test
    fun `unsorted entry dates do not break streak calculations`() {
        val today = LocalDate.of(2026, 10, 27)
        val dates = setOf(
            today.minusDays(4),
            today.minusDays(1),
            today,
            today.minusDays(3),
            today.minusDays(2)
        )
        assertEquals(5, StreakCalculator.current(dates, today))
        assertEquals(5, StreakCalculator.longest(dates))
    }

    // ==========================================
    // 4. Mood Mosaic & Annual Metric Boundaries
    // ==========================================

    @Test
    fun `empty year in pixels produces zero logged entries and full stability`() {
        val stats = MoodMosaicEngine.calculateStats(emptyMap())
        assertEquals(0, stats.totalLogged)
        assertEquals(null, stats.dominantMood)
        assertEquals(1.0, stats.stabilityScore, 0.001)
    }

    @Test
    fun `identical mood for all 365 days produces 100 percent mood and 1 point 0 stability`() {
        val start = LocalDate.of(2026, 1, 1)
        val fullYear = (0 until 365).associate {
            start.plusDays(it.toLong()) to Mood.HAPPY
        }

        val stats = MoodMosaicEngine.calculateStats(fullYear)
        assertEquals(365, stats.totalLogged)
        assertEquals(100.0, stats.moodPercentages[Mood.HAPPY]!!, 0.001)
        assertEquals(Mood.HAPPY, stats.dominantMood)
        assertEquals(1.0, stats.stabilityScore, 0.001)
    }

    // ==========================================
    // 5. App Lock & Brute Force Throttling Escalation
    // ==========================================

    @Test
    fun `pin throttling escalates lockout duration exponentially up to maximum cap`() {
        // Attempt 5 -> 30s
        val res5 = AppLockSecurityEngine.calculateThrottling(5) as AppLockSecurityEngine.PinResult.LockedOut
        assertEquals(30L, res5.lockoutSeconds)

        // Attempt 6 -> 60s
        val res6 = AppLockSecurityEngine.calculateThrottling(6) as AppLockSecurityEngine.PinResult.LockedOut
        assertEquals(60L, res6.lockoutSeconds)

        // Attempt 7 -> 120s
        val res7 = AppLockSecurityEngine.calculateThrottling(7) as AppLockSecurityEngine.PinResult.LockedOut
        assertEquals(120L, res7.lockoutSeconds)

        // Attempt 8 -> 240s
        val res8 = AppLockSecurityEngine.calculateThrottling(8) as AppLockSecurityEngine.PinResult.LockedOut
        assertEquals(240L, res8.lockoutSeconds)

        // Attempt 9+ -> capped at 240s (coerceAtMost(3) -> 30 * 2^3 = 240s)
        val res9 = AppLockSecurityEngine.calculateThrottling(9) as AppLockSecurityEngine.PinResult.LockedOut
        assertEquals(240L, res9.lockoutSeconds)
    }

    // ==========================================
    // 6. Zip-Slip & Path Traversal Variations
    // ==========================================

    @Test
    fun `path validation rejects diverse traversal payloads`() {
        val attackVectors = listOf(
            "..",
            "../secret.txt",
            "dir/../../../root.key",
            "files\\malicious.bin",
            "/data/user/0/databases/app.db",
            "C:/Windows/System32",
            "   ",
            "\t\n"
        )

        for (vector in attackVectors) {
            try {
                BackupArchiveManager.validateArchivePath(vector)
                fail("Vector '$vector' should have been rejected")
            } catch (e: IllegalArgumentException) {
                // Successfully rejected
            }
        }
    }
}
