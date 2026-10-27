package com.onelineaday.dailydiary.e2e.tier4

import com.google.gson.Gson
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Tier 4: Real-World Scenarios and End-to-End Application Workflows.
 * Simulates complete user lifecycles: 365-day journaling journey, leap year transitions,
 * device migration, disaster recovery, pattern insights, and emergency recovery.
 */
class Tier4RealWorldWorkflowTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val gson = Gson()
    private val cryptoEngine = ReferenceBackupCryptoEngine(iterations = 1_000)

    private data class RealWorldManifest(
        val appId: String,
        val formatVersion: Int,
        val entries: List<RealWorldEntryDto>,
        val frozenDates: List<String>
    )

    private data class RealWorldEntryDto(
        val date: String,
        val content: String,
        val mood: String,
        val promptId: String? = null
    )

    @Test
    fun `workflow 1 - full 365 day journaling journey with prompt rotation, streak freezes and annual export`() {
        val year = 2026
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)

        val loggedEntries = mutableMapOf<LocalDate, RealWorldEntryDto>()
        val moodMap = mutableMapOf<LocalDate, Mood>()

        // User took a 3-day summer vacation on July 4, 5, 6
        val vacationDates = setOf(
            LocalDate.of(year, 7, 4),
            LocalDate.of(year, 7, 5),
            LocalDate.of(year, 7, 6)
        )

        var cursor = startDate
        while (!cursor.isAfter(endDate)) {
            if (cursor in vacationDates) {
                // Vacation: user activates streak freeze for this missed day
            } else {
                val prompt = PromptRotationEngine.promptForDate(cursor)
                val mood = if (cursor.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                    Mood.EXCITED
                } else {
                    Mood.HAPPY
                }
                val entry = RealWorldEntryDto(
                    date = cursor.toString(),
                    content = "Line for $cursor answering prompt '${prompt.text}': Feeling grateful.",
                    mood = mood.name,
                    promptId = prompt.id
                )
                loggedEntries[cursor] = entry
                moodMap[cursor] = mood
            }
            cursor = cursor.plusDays(1)
        }

        // Verification 1: Exactly 362 logged days + 3 vacation days = 365 days
        assertEquals(362, loggedEntries.size)
        assertEquals(3, vacationDates.size)

        // Verification 2: Streak calculation bridges vacation days seamlessly
        val entryDates = loggedEntries.keys
        val finalStreak = StreakCalculator.current(
            entryDates = entryDates,
            today = endDate,
            frozenDates = vacationDates
        )
        assertEquals(362, finalStreak)
        assertEquals(184, StreakCalculator.longest(entryDates))

        // Verification 3: Year in Pixels mood stats
        val stats = MoodMosaicEngine.calculateStats(moodMap)
        assertEquals(362, stats.totalLogged)
        // Weekend EXCITED + Weekday HAPPY -> difference is 1 level -> perfectly smooth transitions
        assertEquals(1.0, stats.stabilityScore, 0.001)
        assertTrue(stats.moodCounts[Mood.HAPPY]!! > 0)
        assertTrue(stats.moodCounts[Mood.EXCITED]!! > 0)

        // Verification 4: Encrypted archive generation
        val manifest = RealWorldManifest(
            appId = "com.onelineaday.dailydiary",
            formatVersion = 1,
            entries = loggedEntries.values.toList(),
            frozenDates = vacationDates.map { it.toString() }
        )
        val jsonBytes = gson.toJson(manifest).toByteArray(Charsets.UTF_8)
        val cipherOut = ByteArrayOutputStream()
        val encryptResult = cryptoEngine.encryptStream(
            passphrase = "YearEndEncryptedArchive2026!".toCharArray(),
            plainInput = ByteArrayInputStream(jsonBytes),
            cipherOutput = cipherOut
        )
        assertTrue("365-day archive must encrypt successfully", encryptResult.isSuccess)
        assertTrue("Ciphertext must be generated", cipherOut.size() > 40)
    }

    @Test
    fun `workflow 2 - full 366 day leap year journey and subsequent year flashback observation`() {
        val leapYear = 2024
        val leapDay = LocalDate.of(leapYear, 2, 29)

        // In 2024, user records a special milestone memory on Feb 29
        val leapMemory = "Milestone on leap day: Finished writing my personal memoir draft."
        val year2024Entries = mapOf(
            LocalDate.of(2024, 2, 28) to Mood.HAPPY,
            leapDay to Mood.EXCITED,
            LocalDate.of(2024, 3, 1) to Mood.HAPPY
        )

        // Streak across Feb 28 -> Feb 29 -> Mar 1
        val streakIn2024 = StreakCalculator.current(year2024Entries.keys, LocalDate.of(2024, 3, 1))
        assertEquals(3, streakIn2024)

        // 1 Year later: Feb 28, 2025 (non-leap year)
        val flashbacksIn2025 = LeapDayFlashbackEngine.computeFlashbackTargets(
            currentDate = LocalDate.of(2025, 2, 28),
            historyYears = 1
        )
        val leapObservation2025 = flashbacksIn2025.firstOrNull { it.isLeapDayObservation }
        assertNotNull("Non-leap Feb 28 must surface historical leap day memory", leapObservation2025)
        assertEquals(leapDay, leapObservation2025!!.queryDate)
        assertTrue(leapObservation2025.isLeapDayObservation)

        // 4 Years later: Feb 29, 2028 (next leap year)
        val flashbacksIn2028 = LeapDayFlashbackEngine.computeFlashbackTargets(
            currentDate = LocalDate.of(2028, 2, 29),
            historyYears = 4
        )
        val leapMatch2028 = flashbacksIn2028.firstOrNull { it.queryDate == leapDay }
        assertNotNull("Next leap year Feb 29 must directly match 2024 leap day", leapMatch2028)
        assertFalse("Direct leap match does not need fallback observation badge", leapMatch2028!!.isLeapDayObservation)
    }

    @Test
    fun `workflow 3 - device migration pipeline from legacy zip to modern encrypted container`() {
        // Step 1: User exports from legacy version producing a ZIP archive with PK\x03\x04 magic
        val legacyArchiveFile = tempFolder.newFile("legacy_export.zip")
        val sampleLegacyEntries = listOf(
            RealWorldEntryDto("2026-01-01", "New year beginnings", "GREAT"),
            RealWorldEntryDto("2026-01-02", "First working day", "GOOD")
        )
        val legacyManifestJson = gson.toJson(
            RealWorldManifest(
                appId = "com.onelineaday.dailydiary",
                formatVersion = 1,
                entries = sampleLegacyEntries,
                frozenDates = emptyList()
            )
        )

        // Write legacy ZIP magic
        FileOutputStream(legacyArchiveFile).use { out ->
            out.write(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            out.write(legacyManifestJson.toByteArray(Charsets.UTF_8))
        }

        // Step 2: New device inspects archive and detects LegacyZip
        val containerType = cryptoEngine.detectContainerType(legacyArchiveFile)
        assertEquals(BackupContainerType.LegacyZip, containerType)

        // Step 3: Device upgrades container by encrypting with user recovery passphrase
        val upgradedFile = tempFolder.newFile("upgraded_backup.olad.enc")
        val userPassphrase = "UpgradeMigrationPassphrase2026!".toCharArray()

        FileOutputStream(upgradedFile).use { cipherOut ->
            cryptoEngine.encryptStream(
                passphrase = userPassphrase,
                plainInput = ByteArrayInputStream(legacyManifestJson.toByteArray(Charsets.UTF_8)),
                cipherOutput = cipherOut
            )
        }

        // Step 4: Verify upgraded container is identified as Encrypted and restores completely
        val upgradedType = cryptoEngine.detectContainerType(upgradedFile)
        assertTrue(upgradedType is BackupContainerType.Encrypted)

        val decryptedOut = ByteArrayOutputStream()
        cryptoEngine.decryptStream(
            passphrase = userPassphrase,
            cipherInput = upgradedFile.inputStream(),
            plainOutput = decryptedOut
        )
        val restoredManifest = gson.fromJson(decryptedOut.toString(Charsets.UTF_8.name()), RealWorldManifest::class.java)
        assertEquals(2, restoredManifest.entries.size)
        assertEquals("New year beginnings", restoredManifest.entries[0].content)
    }

    @Test
    fun `workflow 4 - disaster recovery on network packet corruption with clean zero state write`() {
        val passphrase = "DisasterRecoveryPass2026".toCharArray()
        val originalPayload = "Critical lifelong journal memory data"

        val cipherOut = ByteArrayOutputStream()
        cryptoEngine.encryptStream(
            passphrase = passphrase,
            plainInput = ByteArrayInputStream(originalPayload.toByteArray(Charsets.UTF_8)),
            cipherOutput = cipherOut
        )
        val validCipherBytes = cipherOut.toByteArray()

        // Simulate corrupted transmission (packet bit-flip in transit)
        val corruptedBytes = validCipherBytes.clone()
        val tamperIndex = (validCipherBytes.size / 2)
        corruptedBytes[tamperIndex] = (corruptedBytes[tamperIndex].toInt() xor 0xAA).toByte()

        // Restore attempt 1: Corrupted data
        val failedOut = ByteArrayOutputStream()
        val failedResult = cryptoEngine.decryptStream(
            passphrase = passphrase,
            cipherInput = ByteArrayInputStream(corruptedBytes),
            plainOutput = failedOut
        )
        assertTrue("Corrupted stream must fail", failedResult.isFailure)
        assertEquals("No partial bytes committed to disk/db", 0, failedOut.size())

        // Restore attempt 2: Retried with intact data
        val successOut = ByteArrayOutputStream()
        val successResult = cryptoEngine.decryptStream(
            passphrase = passphrase,
            cipherInput = ByteArrayInputStream(validCipherBytes),
            plainOutput = successOut
        )
        assertTrue("Intact stream must succeed", successResult.isSuccess)
        assertEquals(originalPayload, successOut.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `workflow 5 - annual life pattern insights and weekday mood correlation`() {
        val year = 2026
        val entries = mutableMapOf<LocalDate, Mood>()

        var date = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)

        while (!date.isAfter(yearEnd)) {
            // Pattern: Sundays and Saturdays are EXCITED; Fridays and weekdays are HAPPY; Wednesdays are NEUTRAL; Mondays are TIRED
            val mood = when (date.dayOfWeek) {
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> Mood.EXCITED
                DayOfWeek.FRIDAY -> Mood.HAPPY
                DayOfWeek.TUESDAY, DayOfWeek.THURSDAY -> Mood.HAPPY
                DayOfWeek.WEDNESDAY -> Mood.NEUTRAL
                DayOfWeek.MONDAY -> Mood.TIRED
            }
            entries[date] = mood
            date = date.plusDays(1)
        }

        // Evaluate factual insights
        val weekdayMoods = entries.entries.groupBy { it.key.dayOfWeek }

        val mondayMoods = weekdayMoods[DayOfWeek.MONDAY]!!.map { it.value }
        val sundayMoods = weekdayMoods[DayOfWeek.SUNDAY]!!.map { it.value }

        // Factual verification without psychiatric diagnosis
        assertTrue("All Sunday entries in pattern are EXCITED", sundayMoods.all { it == Mood.EXCITED })
        assertTrue("All Monday entries in pattern are TIRED", mondayMoods.all { it == Mood.TIRED })

        val overallStats = MoodMosaicEngine.calculateStats(entries)
        assertEquals(365, overallStats.totalLogged)
        assertTrue("Dominant mood should be HAPPY or EXCITED", overallStats.dominantMood in listOf(Mood.HAPPY, Mood.EXCITED))
    }

    @Test
    fun `workflow 6 - security lockout throttling with emergency recovery`() {
        val userPin = "2468"
        val recoveryPassphrase = "EmergencyMasterPassphrase2026!".toCharArray()
        val hash = AppLockSecurityEngine.hashPinSalted(userPin)

        var failedCount = 0

        // User enters wrong PIN 5 times (brute force or forgotten)
        for (i in 1..5) {
            val isMatch = AppLockSecurityEngine.verifyPinConstantTime(hash, "0000")
            assertFalse(isMatch)
            failedCount++
        }

        // Locked out!
        val throttling = AppLockSecurityEngine.calculateThrottling(failedCount)
        assertTrue("User must be locked out after 5 attempts", throttling is AppLockSecurityEngine.PinResult.LockedOut)
        val lockoutSeconds = (throttling as AppLockSecurityEngine.PinResult.LockedOut).lockoutSeconds
        assertEquals(30L, lockoutSeconds)

        // Emergency recovery: User unlocks using backup master recovery passphrase
        val emergencyTestPayload = "Secret memories unlocked via recovery passphrase"
        val cipherOut = ByteArrayOutputStream()
        val encryptResult = cryptoEngine.encryptStream(
            passphrase = recoveryPassphrase,
            plainInput = ByteArrayInputStream(emergencyTestPayload.toByteArray()),
            cipherOutput = cipherOut
        )
        assertTrue(encryptResult.isSuccess)

        val restoredOut = ByteArrayOutputStream()
        val recoveryResult = cryptoEngine.decryptStream(
            passphrase = recoveryPassphrase,
            cipherInput = ByteArrayInputStream(cipherOut.toByteArray()),
            plainOutput = restoredOut
        )
        assertTrue("Emergency recovery must succeed", recoveryResult.isSuccess)
        assertEquals(emergencyTestPayload, restoredOut.toString(Charsets.UTF_8.name()))
    }
}
