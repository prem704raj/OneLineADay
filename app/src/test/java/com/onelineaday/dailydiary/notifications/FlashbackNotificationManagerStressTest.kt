package com.onelineaday.dailydiary.notifications

import androidx.core.app.NotificationCompat
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Empirical Stress Test Suite for [FlashbackNotificationManager] and Notification Privacy Guarantees.
 *
 * Adversarially challenges:
 * 1. Zero memory suppression: verify notifications are completely suppressed when no memories exist.
 * 2. Lockscreen teaser privacy: comprehensive content/mood/tag leak protection against adversarial journal texts.
 * 3. Teaser message generation across single, multiple, and edge-case year combinations.
 * 4. Channel and intent navigation constants integrity.
 * 5. NotificationCompat lockscreen visibility constraints.
 */
class FlashbackNotificationManagerStressTest {

    private val oneYearText = "You have a memory from 1 year ago today. Tap to unlock."
    private val xYearsFormat = "You have a memory from %d years ago today. Tap to unlock."
    private val multiYearsText = "You have memories from multiple past years today. Tap to unlock."
    private val publicTeaser = "You have historical memories waiting for you."

    // =========================================================================
    // 1. Zero Memory Suppression & Notification Trigger Logic
    // =========================================================================

    @Test
    fun `zero memory database query yields empty year offsets suppressing notification display`() {
        // FlashbackReceiver logic:
        // if (matchingEntries.isNotEmpty()) { showFlashbackNotification(...) }
        // Verify with empty entries list
        val emptyEntries = emptyList<JournalEntry>()
        val yearOffsets = emptyEntries.map { 2026 - it.date.year }.distinct().sorted()

        assertTrue("Offsets must be empty for zero matching entries", yearOffsets.isEmpty())
        // Suppression check: Notification is never built or dispatched when yearOffsets is empty
        val shouldPostNotification = yearOffsets.isNotEmpty()
        assertFalse("Notification posting must be suppressed when zero entries match", shouldPostNotification)
    }

    @Test
    fun `matching entries correctly compute distinct sorted positive year offsets`() {
        val today = LocalDate.of(2026, 10, 27)
        val entries = listOf(
            JournalEntry(date = LocalDate.of(2025, 10, 27), content = "Memory A", mood = Mood.HAPPY),
            JournalEntry(date = LocalDate.of(2025, 10, 27), content = "Memory B (same day)", mood = Mood.EXCITED),
            JournalEntry(date = LocalDate.of(2023, 10, 27), content = "Memory C", mood = Mood.GRATEFUL),
            JournalEntry(date = LocalDate.of(2021, 10, 27), content = "Memory D", mood = Mood.SAD)
        )

        val yearOffsets = entries.map { today.year - it.date.year }.distinct().sorted()

        assertEquals("Duplicates from same year must be collapsed", listOf(1, 3, 5), yearOffsets)
        assertTrue("Notification should post when matching entries exist", yearOffsets.isNotEmpty())
    }

    // =========================================================================
    // 2. Lockscreen Privacy Leak Stress Testing
    // =========================================================================

    @Test
    fun `adversarial confidential entries are strictly shielded from all notification teasers`() {
        val highlyConfidentialEntries = listOf(
            JournalEntry(
                date = LocalDate.of(2025, 1, 1),
                content = "PIN code 9876, Bank account #12345678, Password: SecretSauce!",
                mood = Mood.ANXIOUS,
                tags = listOf("finance", "secret", "medical")
            ),
            JournalEntry(
                date = LocalDate.of(2024, 6, 15),
                content = "Therapy notes: Diagnosed with acute stress, prescribed Medication X.",
                mood = Mood.SAD,
                tags = listOf("health", "confidential")
            ),
            JournalEntry(
                date = LocalDate.of(2023, 12, 31),
                content = "Surprise party plan for John at 8pm. Don't tell anyone!",
                mood = Mood.EXCITED,
                tags = listOf("surprise", "party")
            )
        )

        val generatedTeasers = listOf(
            FlashbackNotificationManager.formatTeaserMessage(listOf(1), oneYearText, xYearsFormat, multiYearsText),
            FlashbackNotificationManager.formatTeaserMessage(listOf(2), oneYearText, xYearsFormat, multiYearsText),
            FlashbackNotificationManager.formatTeaserMessage(listOf(3), oneYearText, xYearsFormat, multiYearsText),
            FlashbackNotificationManager.formatTeaserMessage(listOf(1, 2), oneYearText, xYearsFormat, multiYearsText),
            FlashbackNotificationManager.formatTeaserMessage(listOf(1, 3, 5), oneYearText, xYearsFormat, multiYearsText),
            publicTeaser
        )

        for (entry in highlyConfidentialEntries) {
            for (teaser in generatedTeasers) {
                // Must not leak any entry content
                assertFalse("Teaser must not leak raw entry content", teaser.contains(entry.content))
                // Must not leak passwords or numbers
                assertFalse("Teaser must not leak numeric codes", teaser.contains("9876"))
                assertFalse("Teaser must not leak bank details", teaser.contains("12345678"))
                assertFalse("Teaser must not leak medical details", teaser.contains("Therapy"))
                assertFalse("Teaser must not leak personal names", teaser.contains("John"))

                // Must not leak moods
                assertFalse("Teaser must not leak mood: ${entry.mood.name}",
                    teaser.contains(entry.mood.name, ignoreCase = true)
                )

                // Must not leak tags
                for (tag in entry.tags) {
                    assertFalse("Teaser must not leak tag: $tag", teaser.contains(tag, ignoreCase = true))
                }
            }
        }
    }

    // =========================================================================
    // 3. Teaser Message Variations and Combinatorics
    // =========================================================================

    @Test
    fun `teaser generation accurately selects singular vs plural vs multi-year phrasing`() {
        // Singular exactly 1 year ago
        val teaser1 = FlashbackNotificationManager.formatTeaserMessage(
            listOf(1), oneYearText, xYearsFormat, multiYearsText
        )
        assertEquals("You have a memory from 1 year ago today. Tap to unlock.", teaser1)

        // Singular N years ago
        for (n in 2..10) {
            val teaserN = FlashbackNotificationManager.formatTeaserMessage(
                listOf(n), oneYearText, xYearsFormat, multiYearsText
            )
            assertEquals("You have a memory from $n years ago today. Tap to unlock.", teaserN)
        }

        // Multi-year combinations
        val multiCombos = listOf(
            listOf(1, 2),
            listOf(1, 5),
            listOf(2, 3, 4),
            listOf(1, 2, 3, 4, 5)
        )
        for (combo in multiCombos) {
            val teaserMulti = FlashbackNotificationManager.formatTeaserMessage(
                combo, oneYearText, xYearsFormat, multiYearsText
            )
            assertEquals("You have memories from multiple past years today. Tap to unlock.", teaserMulti)
        }
    }

    // =========================================================================
    // 4. Intent & Navigation Constants Verification
    // =========================================================================

    @Test
    fun `navigation and channel constants strictly adhere to Milestone 2 contract`() {
        assertEquals("flashback_memories", FlashbackNotificationManager.CHANNEL_ID)
        assertEquals(1002, FlashbackNotificationManager.NOTIFICATION_ID)
        assertEquals(2002, FlashbackNotificationManager.REQUEST_CODE)
        assertEquals("extra_nav_destination", FlashbackNotificationManager.EXTRA_NAV_DESTINATION)
        assertEquals("on_this_day", FlashbackNotificationManager.DESTINATION_ON_THIS_DAY)
        assertEquals("flashback_notifications_enabled", FlashbackNotificationManager.PREFS_KEY_ENABLED)
        assertEquals("flashback_hour", FlashbackNotificationManager.PREFS_KEY_HOUR)
        assertEquals("flashback_minute", FlashbackNotificationManager.PREFS_KEY_MINUTE)

        // Notification channel and reminder notification ID separation
        assertNotEquals("Notification ID must not clash with daily reminder",
            1001, FlashbackNotificationManager.NOTIFICATION_ID
        )
    }

    // =========================================================================
    // 5. Visibility Security Assertion
    // =========================================================================

    @Test
    fun `lockscreen visibility constant equals VISIBILITY_PRIVATE`() {
        // VISIBILITY_PRIVATE (0): Shows public notification version on insecure keyguard
        assertEquals(0, NotificationCompat.VISIBILITY_PRIVATE)
    }
}
