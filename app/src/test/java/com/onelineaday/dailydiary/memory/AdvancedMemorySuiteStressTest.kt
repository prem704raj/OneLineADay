package com.onelineaday.dailydiary.memory

import com.onelineaday.dailydiary.capsule.TimeCapsule
import com.onelineaday.dailydiary.constellation.MemoryConstellationEngine
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import com.onelineaday.dailydiary.search.SmartSearchEngine
import com.onelineaday.dailydiary.video.MonthlyReelEngine
import com.onelineaday.dailydiary.zen.ZenGardenEngine
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class AdvancedMemorySuiteStressTest {

    @Test
    fun testMonthlyReelBuildsChronologicalPhotosOnly() {
        val nov2026 = YearMonth.of(2026, 11)
        val entries = listOf(
            JournalEntry(date = LocalDate.of(2026, 11, 5), content = "Photo day", photoUri = "file:///img1.jpg"),
            JournalEntry(date = LocalDate.of(2026, 11, 2), content = "No photo day"),
            JournalEntry(date = LocalDate.of(2026, 11, 1), content = "Gallery day", mediaUris = listOf("file:///img0.jpg")),
            JournalEntry(date = LocalDate.of(2026, 10, 31), content = "Wrong month", photoUri = "file:///oct.jpg")
        )

        val reel = MonthlyReelEngine.buildMonthlyReel(entries, nov2026)

        // Only Nov 1 and Nov 5 have photos
        assertEquals(2, reel.size)
        assertEquals(LocalDate.of(2026, 11, 1), reel[0].date)
        assertEquals(LocalDate.of(2026, 11, 5), reel[1].date)
        assertEquals("file:///img0.jpg", reel[0].photoUri)
        assertEquals("file:///img1.jpg", reel[1].photoUri)
    }

    @Test
    fun testTimeCapsuleLockStateAndDaysRemaining() {
        val today = LocalDate.of(2026, 10, 27)
        val pastDate = today.minusDays(10)
        val futureDate = today.plusDays(30)

        val readyCapsule = TimeCapsule(
            title = "Past Letter",
            messageToFutureSelf = "Hello future!",
            sealedDate = today.minusDays(365),
            unlockDate = pastDate
        )

        assertTrue(readyCapsule.isReadyToUnlock(today))
        assertEquals(0L, readyCapsule.daysRemaining(today))

        val lockedCapsule = TimeCapsule(
            title = "Future Letter",
            messageToFutureSelf = "Wait for it!",
            sealedDate = today,
            unlockDate = futureDate
        )

        assertFalse(lockedCapsule.isReadyToUnlock(today))
        assertEquals(30L, lockedCapsule.daysRemaining(today))
    }

    @Test
    fun testSmartSearchScoringAndIntentMatching() {
        val entries = listOf(
            JournalEntry(date = LocalDate.of(2026, 5, 10), content = "Sitting on the porch listening to rainfall", mood = Mood.GRATEFUL),
            JournalEntry(date = LocalDate.of(2026, 7, 20), content = "Excited about the road trip to mountains", mood = Mood.EXCITED, tags = listOf("travel")),
            JournalEntry(date = LocalDate.of(2026, 8, 15), content = "Tough day with flat tire in the rain", mood = Mood.SAD)
        )

        // Exact query
        val rainfallMatches = SmartSearchEngine.search(entries, "rainfall")
        assertTrue(rainfallMatches.isNotEmpty())
        assertEquals(LocalDate.of(2026, 5, 10), rainfallMatches.first().entry.date)

        // Semantic query: "happiest"
        val happyMatches = SmartSearchEngine.search(entries, "happiest")
        assertTrue(happyMatches.isNotEmpty())
        assertTrue("Top match should be excited or grateful", happyMatches.first().entry.mood in setOf(Mood.EXCITED, Mood.GRATEFUL))

        // Tag query: "travel"
        val travelMatches = SmartSearchEngine.search(entries, "travel")
        assertEquals(1, travelMatches.size)
        assertEquals(LocalDate.of(2026, 7, 20), travelMatches.first().entry.date)
    }

    @Test
    fun testConstellationGraphBoundsAndEdges() {
        val entries = listOf(
            JournalEntry(date = LocalDate.of(2026, 1, 1), content = "New year start", tags = listOf("milestone")),
            JournalEntry(date = LocalDate.of(2026, 1, 2), content = "Day 2", tags = listOf("milestone")),
            JournalEntry(date = LocalDate.of(2026, 1, 3), content = "Day 3", tags = listOf("coffee")),
            JournalEntry(date = LocalDate.of(2026, 1, 4), content = "Day 4", tags = listOf("coffee"))
        )

        val graph = MemoryConstellationEngine.buildConstellation(entries)
        assertEquals(4, graph.stars.size)

        for (star in graph.stars) {
            assertTrue("Star X (${star.x}) must be in [0.08, 0.92]", star.x in 0.08f..0.92f)
            assertTrue("Star Y (${star.y}) must be in [0.08, 0.92]", star.y in 0.08f..0.92f)
        }

        assertTrue("Constellation should connect stars sharing tags #milestone and #coffee", graph.edges.isNotEmpty())
    }

    @Test
    fun testZenGardenProgressionTiers() {
        // Streak = 0: All locked
        val state0 = ZenGardenEngine.computeState(0, 0)
        assertTrue(state0.unlockedItems.isEmpty())
        assertEquals("stone", state0.nextItemToUnlock?.id)

        // Streak = 5: Stone (req 3) unlocked, Bamboo (req 7) next
        val state5 = ZenGardenEngine.computeState(5, 5)
        assertEquals(1, state5.unlockedItems.size)
        assertEquals("stone", state5.unlockedItems.first().id)
        assertEquals("bamboo", state5.nextItemToUnlock?.id)
        assertEquals(0.5f, state5.progressToNext, 0.01f) // (5-3)/(7-3) = 2/4 = 0.5

        // Streak = 100+: All 6 items unlocked
        val state100 = ZenGardenEngine.computeState(100, 100)
        assertEquals(6, state100.unlockedItems.size)
        assertNull(state100.nextItemToUnlock)
        assertEquals(1.0f, state100.progressToNext, 0.01f)
    }
}

