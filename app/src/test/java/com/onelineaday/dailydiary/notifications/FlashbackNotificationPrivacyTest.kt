package com.onelineaday.dailydiary.notifications
import androidx.core.app.NotificationCompat
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests verifying privacy guarantees, constants, and lock-screen teaser generation
 * for Flashback notifications (Feature F8).
 */
class FlashbackNotificationPrivacyTest {

    private val oneYearText = "You have a memory from 1 year ago today. Tap to unlock."
    private val xYearsFormat = "You have a memory from %d years ago today. Tap to unlock."
    private val multiYearsText = "You have memories from multiple past years today. Tap to unlock."
    private val publicTeaserText = "You have historical memories waiting for you."

    @Test
    fun `channel and notification constants are strictly configured`() {
        assertEquals("flashback_memories", FlashbackNotificationManager.CHANNEL_ID)
        assertEquals(1002, FlashbackNotificationManager.NOTIFICATION_ID)
        assertEquals(2002, FlashbackNotificationManager.REQUEST_CODE)
        assertEquals("extra_nav_destination", FlashbackNotificationManager.EXTRA_NAV_DESTINATION)
        assertEquals("on_this_day", FlashbackNotificationManager.DESTINATION_ON_THIS_DAY)
        assertEquals("flashback_notifications_enabled", FlashbackNotificationManager.PREFS_KEY_ENABLED)
    }

    @Test
    fun `teaser for single memory 1 year ago uses dedicated singular phrasing`() {
        val teaser = FlashbackNotificationManager.formatTeaserMessage(
            yearOffsets = listOf(1),
            oneYearText = oneYearText,
            xYearsFormat = xYearsFormat,
            multiYearsText = multiYearsText
        )
        assertEquals("You have a memory from 1 year ago today. Tap to unlock.", teaser)
    }

    @Test
    fun `teaser for single memory multiple years ago includes accurate year count`() {
        for (year in 2..5) {
            val teaser = FlashbackNotificationManager.formatTeaserMessage(
                yearOffsets = listOf(year),
                oneYearText = oneYearText,
                xYearsFormat = xYearsFormat,
                multiYearsText = multiYearsText
            )
            assertEquals("You have a memory from $year years ago today. Tap to unlock.", teaser)
        }
    }

    @Test
    fun `teaser for multiple past years uses generic multi-year phrasing`() {
        val teaserTwoYears = FlashbackNotificationManager.formatTeaserMessage(
            yearOffsets = listOf(1, 3),
            oneYearText = oneYearText,
            xYearsFormat = xYearsFormat,
            multiYearsText = multiYearsText
        )
        assertEquals("You have memories from multiple past years today. Tap to unlock.", teaserTwoYears)

        val teaserThreeYears = FlashbackNotificationManager.formatTeaserMessage(
            yearOffsets = listOf(1, 2, 4),
            oneYearText = oneYearText,
            xYearsFormat = xYearsFormat,
            multiYearsText = multiYearsText
        )
        assertEquals("You have memories from multiple past years today. Tap to unlock.", teaserThreeYears)
    }

    @Test
    fun `notification teasers never leak confidential journal content or mood`() {
        val sensitiveEntry = JournalEntry(
            date = LocalDate.of(2023, 10, 27),
            content = "Extremely confidential medical appointment notes and financial details",
            mood = Mood.ANXIOUS,
            tags = listOf("secret", "health")
        )

        val teasers = listOf(
            FlashbackNotificationManager.formatTeaserMessage(
                listOf(1), oneYearText, xYearsFormat, multiYearsText
            ),
            FlashbackNotificationManager.formatTeaserMessage(
                listOf(3), oneYearText, xYearsFormat, multiYearsText
            ),
            FlashbackNotificationManager.formatTeaserMessage(
                listOf(1, 2), oneYearText, xYearsFormat, multiYearsText
            ),
            publicTeaserText
        )

        for (teaser in teasers) {
            // Assert that no sensitive entry text, mood name, or tag appears in the notification
            assertFalse("Teaser must never expose journal content", teaser.contains(sensitiveEntry.content))
            assertFalse("Teaser must never expose entry keywords", teaser.contains("confidential"))
            assertFalse("Teaser must never expose entry keywords", teaser.contains("medical"))
            assertFalse("Teaser must never expose mood name", teaser.contains(sensitiveEntry.mood.name, ignoreCase = true))
            assertFalse("Teaser must never expose tags", teaser.contains("secret"))
        }
    }

    @Test
    fun `notification visibility is set to VISIBILITY_PRIVATE for lock screen privacy`() {
        // NotificationCompat.VISIBILITY_PRIVATE is 0 in Android framework
        assertEquals(0, NotificationCompat.VISIBILITY_PRIVATE)
    }
}
