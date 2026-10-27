package com.onelineaday.dailydiary.e2e.tier3

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.onelineaday.dailydiary.data.Mood
import com.onelineaday.dailydiary.data.StreakCalculator
import com.onelineaday.dailydiary.e2e.engine.AppLockSecurityEngine
import com.onelineaday.dailydiary.e2e.engine.BackupContainerType
import com.onelineaday.dailydiary.e2e.engine.EncryptedBackupHeader
import com.onelineaday.dailydiary.e2e.engine.LeapDayFlashbackEngine
import com.onelineaday.dailydiary.e2e.engine.MoodMosaicEngine
import com.onelineaday.dailydiary.e2e.engine.PromptRotationEngine
import com.onelineaday.dailydiary.e2e.engine.ReferenceBackupCryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * Tier 3: Cross-Feature Integration Tests.
 * Evaluates combinations across Encryption, Streaks, App Lock, Flashbacks, Mood Mosaic, and Multi-Journaling.
 */
class Tier3CrossFeatureIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val gson = Gson()
    private val cryptoEngine = ReferenceBackupCryptoEngine(iterations = 1_000)

    private data class TestJournalState(
        val journalId: String,
        val entries: List<TestEntryDto>,
        val frozenDates: List<String>
    )

    private data class TestEntryDto(
        val date: String,
        val mood: String,
        val content: String,
        val promptId: String? = null
    )

    @Test
    fun `encrypted backup export and restore preserves streak freeze history and streak continuity`() {
        val today = LocalDate.of(2026, 10, 27)
        val entries = setOf(today, today.minusDays(2), today.minusDays(3))
        val frozen = setOf(today.minusDays(1))

        // Pre-backup streak calculation
        val streakBefore = StreakCalculator.current(entries, today, frozen)
        assertEquals(3, streakBefore)

        // Serialize state to JSON manifest
        val state = TestJournalState(
            journalId = "main_journal",
            entries = entries.map { TestEntryDto(it.toString(), Mood.HAPPY.name, "Journal entry on $it") },
            frozenDates = frozen.map { it.toString() }
        )
        val jsonPayload = gson.toJson(state)

        // Encrypt stream
        val passphrase = "MasterBackupPassphrase2026".toCharArray()
        val cipherOut = ByteArrayOutputStream()
        val encryptResult = cryptoEngine.encryptStream(
            passphrase = passphrase,
            plainInput = ByteArrayInputStream(jsonPayload.toByteArray(Charsets.UTF_8)),
            cipherOutput = cipherOut
        )
        assertTrue(encryptResult.isSuccess)

        // Decrypt stream
        val plainOut = ByteArrayOutputStream()
        val decryptResult = cryptoEngine.decryptStream(
            passphrase = passphrase,
            cipherInput = ByteArrayInputStream(cipherOut.toByteArray()),
            plainOutput = plainOut
        )
        assertTrue(decryptResult.isSuccess)

        // Deserialize and re-evaluate streak
        val restoredState = gson.fromJson(plainOut.toString(Charsets.UTF_8.name()), TestJournalState::class.java)
        val restoredEntries = restoredState.entries.map { LocalDate.parse(it.date) }.toSet()
        val restoredFrozen = restoredState.frozenDates.map { LocalDate.parse(it) }.toSet()

        val streakAfter = StreakCalculator.current(restoredEntries, today, restoredFrozen)
        assertEquals("Streak after encrypted restore must match pre-backup streak", streakBefore, streakAfter)
    }

    @Test
    fun `app lock active state enforces privacy masking on flashback preview and widget content`() {
        val sampleEntry = "Went for a 5k morning run with Sarah in Central Park."

        fun getPreview(isAppLockEnabled: Boolean, isAuthenticated: Boolean): String {
            return if (AppLockSecurityEngine.shouldMaskContent(isAppLockEnabled, isAuthenticated)) {
                "Memories from 1 year ago ready (Unlock to view)"
            } else {
                sampleEntry
            }
        }

        // Lock enabled, unauthenticated -> masked teaser only
        val maskedPreview = getPreview(isAppLockEnabled = true, isAuthenticated = false)
        assertEquals("Memories from 1 year ago ready (Unlock to view)", maskedPreview)
        assertFalse(maskedPreview.contains("Sarah"))

        // Authenticated -> full plaintext revealed
        val unmaskedPreview = getPreview(isAppLockEnabled = true, isAuthenticated = true)
        assertEquals(sampleEntry, unmaskedPreview)

        // Lock disabled -> full plaintext revealed
        val unmaskedDisabled = getPreview(isAppLockEnabled = false, isAuthenticated = false)
        assertEquals(sampleEntry, unmaskedDisabled)
    }

    @Test
    fun `leap day flashback retrieval combined with mood mosaic stability metrics`() {
        val leapDate = LocalDate.of(2024, 2, 29)
        val feb28NonLeap = LocalDate.of(2025, 2, 28)

        // Flashback query on Feb 28, 2025 observes Feb 29, 2024 with leap badge
        val targets = LeapDayFlashbackEngine.computeFlashbackTargets(feb28NonLeap, historyYears = 1)
        val leapTarget = targets.firstOrNull { it.isLeapDayObservation }
        assertTrue(leapTarget != null)
        assertEquals(leapDate, leapTarget!!.queryDate)

        // Add 2024 leap day to 2024 entries and verify Year in Pixels includes it without crashing
        val year2024Entries = mapOf(
            LocalDate.of(2024, 2, 28) to Mood.HAPPY,
            LocalDate.of(2024, 2, 29) to Mood.EXCITED,
            LocalDate.of(2024, 3, 1) to Mood.HAPPY
        )

        val stats = MoodMosaicEngine.calculateStats(year2024Entries)
        assertEquals(3, stats.totalLogged)
        // Transition HAPPY(4) -> EXCITED(5) -> HAPPY(4) is fully smooth
        assertEquals(1.0, stats.stabilityScore, 0.001)
    }

    @Test
    fun `container sniffer dynamically routes between legacy zip and modern encrypted container`() {
        val encFile = tempFolder.newFile("backup.olad.enc")
        val zipFile = tempFolder.newFile("backup.zip")

        // Populate encrypted container
        FileOutputStream(encFile).use { out ->
            val header = EncryptedBackupHeader(
                magic = EncryptedBackupHeader.EXPECTED_MAGIC,
                version = 1,
                kdfSalt = ByteArray(16),
                kdfIterations = 100_000,
                nonce = ByteArray(12)
            )
            out.write(EncryptedBackupHeader.serialize(header))
            out.write(ByteArray(32))
        }

        // Populate legacy zip
        FileOutputStream(zipFile).use { out ->
            out.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            out.write(ByteArray(32))
        }

        val typeEnc = cryptoEngine.detectContainerType(encFile)
        val typeZip = cryptoEngine.detectContainerType(zipFile)

        assertTrue("Encrypted file must be detected as Encrypted", typeEnc is BackupContainerType.Encrypted)
        assertEquals("Legacy zip file must be detected as LegacyZip", BackupContainerType.LegacyZip, typeZip)
    }

    @Test
    fun `daily spark prompt response integration with mood tagging and streak accumulation`() {
        val today = LocalDate.of(2026, 10, 27)
        val priorEntries = setOf(today.minusDays(1), today.minusDays(2))
        val currentStreak = StreakCalculator.current(priorEntries, today)
        assertEquals(2, currentStreak)

        // Retrieve deterministic prompt for today
        val prompt = PromptRotationEngine.promptForDate(today)
        assertTrue(prompt.text.isNotBlank())

        // User answers prompt and logs entry with Mood.EXCITED
        val userContent = "Answering '${prompt.text}': Grateful for our resilient engineering architecture."
        val newEntry = TestEntryDto(
            date = today.toString(),
            mood = Mood.EXCITED.name,
            content = userContent,
            promptId = prompt.id
        )

        val updatedEntries = priorEntries + today
        val newStreak = StreakCalculator.current(updatedEntries, today)
        assertEquals(3, newStreak)

        val moodMap = mapOf(
            today.minusDays(2) to Mood.HAPPY,
            today.minusDays(1) to Mood.HAPPY,
            today to Mood.EXCITED
        )
        val stats = MoodMosaicEngine.calculateStats(moodMap)
        assertEquals(3, stats.totalLogged)
        assertEquals(1.0, stats.stabilityScore, 0.001)
    }

    @Test
    fun `brute force pin failure escalation halts backup export authorization`() {
        var failedAttempts = 0

        fun canPerformExport(): Boolean {
            val throttling = AppLockSecurityEngine.calculateThrottling(failedAttempts)
            return throttling !is AppLockSecurityEngine.PinResult.LockedOut
        }

        // First 4 attempts fail -> export still permitted
        for (i in 1..4) {
            failedAttempts++
            assertTrue("Export permitted before 5 failed attempts", canPerformExport())
        }

        // 5th attempt fails -> lockout triggered -> export blocked
        failedAttempts++
        assertFalse("Export MUST be blocked once user is locked out", canPerformExport())
    }

    @Test
    fun `isolated duo journal space encryption with independent passphrase`() {
        val personalPass = "PersonalKey123".toCharArray()
        val duoPass = "PartnerKey456".toCharArray()

        val personalData = "Secret personal thoughts".toByteArray()
        val duoData = "Shared trip planning with partner".toByteArray()

        val personalCipherOut = ByteArrayOutputStream()
        cryptoEngine.encryptStream(personalPass, ByteArrayInputStream(personalData), personalCipherOut)

        val duoCipherOut = ByteArrayOutputStream()
        cryptoEngine.encryptStream(duoPass, ByteArrayInputStream(duoData), duoCipherOut)

        // Decrypting Duo container with Personal key must fail
        val crossDecryptDuoWithPersonal = cryptoEngine.decryptStream(
            passphrase = personalPass,
            cipherInput = ByteArrayInputStream(duoCipherOut.toByteArray()),
            plainOutput = ByteArrayOutputStream()
        )
        assertTrue("Personal key must fail on Duo container", crossDecryptDuoWithPersonal.isFailure)

        // Decrypting Personal container with Duo key must fail
        val crossDecryptPersonalWithDuo = cryptoEngine.decryptStream(
            passphrase = duoPass,
            cipherInput = ByteArrayInputStream(personalCipherOut.toByteArray()),
            plainOutput = ByteArrayOutputStream()
        )
        assertTrue("Duo key must fail on Personal container", crossDecryptPersonalWithDuo.isFailure)
    }

    @Test
    fun `multi journal separation maintains disjoint streak and mood analytics`() {
        val today = LocalDate.of(2026, 10, 27)

        // Journal 1 (Personal Daily): 5 consecutive days of EXCITED
        val journal1Entries = (0..4).map { today.minusDays(it.toLong()) }.toSet()
        val journal1Moods = journal1Entries.associateWith { Mood.EXCITED }

        // Journal 2 (Work / Project Log): only 1 entry on today of NEUTRAL
        val journal2Entries = setOf(today)
        val journal2Moods = journal2Entries.associateWith { Mood.NEUTRAL }

        val streak1 = StreakCalculator.current(journal1Entries, today)
        val streak2 = StreakCalculator.current(journal2Entries, today)

        assertEquals(5, streak1)
        assertEquals(1, streak2)

        val stats1 = MoodMosaicEngine.calculateStats(journal1Moods)
        val stats2 = MoodMosaicEngine.calculateStats(journal2Moods)

        assertEquals(Mood.EXCITED, stats1.dominantMood)
        assertEquals(Mood.NEUTRAL, stats2.dominantMood)
    }
}
