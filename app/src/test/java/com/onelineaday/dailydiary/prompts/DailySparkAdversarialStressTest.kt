package com.onelineaday.dailydiary.prompts

import com.onelineaday.dailydiary.e2e.engine.AppLockSecurityEngine
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Empirical Adversarial Stress Test Suite for Daily Spark Prompts & Deterministic Rotation (F10, F11).
 *
 * Verifies:
 * 1. BundledPromptCatalog exhaustive integrity: 320 prompts, 10 categories, 32 per category,
 *    unique IDs, unique texts, properly formatted questions, and dedicated quadrennial leap day prompt.
 * 2. PromptRotationEngine 10-year simulation (3,652 consecutive days):
 *    - Zero duplicate prompt IDs across consecutive days (including year boundaries and leap transitions).
 *    - Strict determinism (same date always returns exact same prompt).
 *    - Full quadrennial Leap Day handling (Feb 29 Day 60, Dec 31 Day 366).
 *    - 100% adherence to category filtering across all dates.
 *    - Extreme custom offset wrapping (handles Int.MIN_VALUE, Int.MAX_VALUE, large negative/positive).
 * 3. AppLockManager security boundary preservation.
 * 4. Adversarial catalog lookup behavior with malicious ID strings.
 */
class DailySparkAdversarialStressTest {

    // -------------------------------------------------------------------------
    // 1. BundledPromptCatalog Exhaustive Validation
    // -------------------------------------------------------------------------

    @Test
    fun testBundledPromptCatalogExhaustiveIntegrity() {
        val allPrompts = BundledPromptCatalog.ALL_PROMPTS
        assertEquals("Catalog must contain exactly 320 prompts", 320, allPrompts.size)

        // Verify distinct IDs
        val ids = allPrompts.map { it.id }
        assertEquals("All 320 prompt IDs must be unique", 320, ids.toSet().size)

        // Verify distinct prompt texts (no duplicate questions)
        val texts = allPrompts.map { it.text.trim().lowercase() }
        assertEquals("All 320 prompt texts must be unique", 320, texts.toSet().size)

        // Verify ID formatting and text quality
        val idRegex = Regex("^prompt_[a-z_]+_\\d+$")
        allPrompts.forEach { prompt ->
            assertTrue(
                "Prompt ID '${prompt.id}' must match format prompt_<category>_<number>",
                idRegex.matches(prompt.id)
            )
            assertTrue("Prompt text for ID '${prompt.id}' must not be blank", prompt.text.isNotBlank())
            assertTrue(
                "Prompt text for ID '${prompt.id}' should be a meaningful question (>= 15 chars)",
                prompt.text.trim().length >= 15
            )
            assertTrue(
                "Prompt text for ID '${prompt.id}' should end with a question mark",
                prompt.text.trim().endsWith("?")
            )
        }

        // Verify category distribution: exactly 10 categories with 32 prompts each
        val categories = PromptCategory.values()
        assertEquals("There must be exactly 10 prompt categories", 10, categories.size)

        categories.forEach { category ->
            val promptsForCat = allPrompts.filter { it.category == category }
            assertEquals(
                "Category ${category.name} must contain exactly 32 prompts",
                32,
                promptsForCat.size
            )
        }
    }

    @Test
    fun testQuadrennialLeapDayPromptIntegrity() {
        val leapPrompt = BundledPromptCatalog.LEAP_DAY_PROMPT
        assertEquals("prompt_leap_day_quadrennial", leapPrompt.id)
        assertEquals(PromptCategory.REFLECTION, leapPrompt.category)
        assertTrue("Leap prompt text must not be blank", leapPrompt.text.isNotBlank())
        assertTrue("Leap prompt text must end with a question mark", leapPrompt.text.trim().endsWith("?"))
        assertTrue("Leap prompt must mention bonus hours", leapPrompt.text.contains("bonus hours", ignoreCase = true))

        // Ensure leap prompt is isolated from standard 320 prompts so rotation math is unskewed
        val allIds = BundledPromptCatalog.ALL_PROMPTS.map { it.id }.toSet()
        assertFalse(
            "Quadrennial leap day prompt must not be part of the standard 320-prompt list",
            allIds.contains(leapPrompt.id)
        )

        // Lookup test
        val resolved = PromptRotationEngine.getPromptById("prompt_leap_day_quadrennial")
        assertNotNull("PromptRotationEngine must resolve leap day prompt by ID", resolved)
        assertEquals(leapPrompt.id, resolved?.id)
        assertEquals(leapPrompt.text, resolved?.text)
    }

    // -------------------------------------------------------------------------
    // 2. PromptRotationEngine 10-Year Simulation (3,652 Consecutive Days)
    // -------------------------------------------------------------------------

    @Test
    fun testTenYearSimulationZeroDuplicateConsecutivePrompts() {
        // 10 full years starting from a leap year: 2024-01-01 through 2033-12-31
        val startDate = LocalDate.of(2024, 1, 1)
        val simulationDays = 3652 // 10 years including 2024, 2028, 2032 leap years

        var previousPrompt: DailySparkPrompt? = null

        for (dayIndex in 0 until simulationDays) {
            val currentDate = startDate.plusDays(dayIndex.toLong())
            val currentPrompt = PromptRotationEngine.promptForDate(currentDate, customOffset = 0)

            assertNotNull("Prompt on $currentDate must never be null", currentPrompt)
            assertTrue("Prompt on $currentDate must have non-blank text", currentPrompt.text.isNotBlank())

            if (previousPrompt != null) {
                assertNotEquals(
                    "Consecutive days ($currentDate vs ${currentDate.minusDays(1)}) must never have duplicate prompt IDs! Found duplicate ID '${currentPrompt.id}'",
                    previousPrompt.id,
                    currentPrompt.id
                )
            }

            previousPrompt = currentPrompt
        }
    }

    @Test
    fun testTenYearSimulationStrictDeterminism() {
        val startDate = LocalDate.of(2024, 1, 1)
        val simulationDays = 3652

        // Query each date multiple times with different iterations to guarantee determinism
        for (dayIndex in 0 until simulationDays step 7) { // Sample every week across 10 years
            val date = startDate.plusDays(dayIndex.toLong())

            val run1 = PromptRotationEngine.promptForDate(date, customOffset = 0)
            val run2 = PromptRotationEngine.promptForDate(date, customOffset = 0)
            val run3 = PromptRotationEngine.promptForDate(date, customOffset = 0)

            assertEquals("Determinism failed for date $date (run 1 vs run 2)", run1.id, run2.id)
            assertEquals("Determinism failed for date $date (run 2 vs run 3)", run2.id, run3.id)
            assertEquals(run1.text, run2.text)
            assertEquals(run1.category, run2.category)
        }
    }

    @Test
    fun testLeapDayAndDay366HandlingAcrossDecade() {
        val leapYears = listOf(2024, 2028, 2032, 2036, 2040)

        for (year in leapYears) {
            val leapDay = LocalDate.of(year, 2, 29)
            assertEquals("Feb 29 must be day 60 in leap year $year", 60, leapDay.dayOfYear)

            // Default offset 0: Dedicated Quadrennial Leap Day Prompt
            val leapPrompt = PromptRotationEngine.promptForDate(leapDay, customOffset = 0)
            assertEquals(
                "Feb 29 in leap year $year must yield the dedicated quadrennial prompt on offset 0",
                BundledPromptCatalog.LEAP_DAY_PROMPT.id,
                leapPrompt.id
            )

            // Shuffled offset: Should return valid catalog prompt without throwing
            val shuffledLeapPrompt = PromptRotationEngine.promptForDate(leapDay, customOffset = 1)
            assertNotNull(shuffledLeapPrompt)
            assertNotEquals("Shuffled leap day must rotate away from dedicated leap prompt", leapPrompt.id, shuffledLeapPrompt.id)

            // Day 366 (Dec 31 of leap year)
            val day366 = LocalDate.of(year, 12, 31)
            assertEquals(366, day366.dayOfYear)

            val promptDay366 = PromptRotationEngine.promptForDate(day366, customOffset = 0)
            assertNotNull("Dec 31 (Day 366) in leap year $year must return non-null prompt", promptDay366)
            assertTrue("Dec 31 (Day 366) prompt text must be non-blank", promptDay366.text.isNotBlank())

            // Boundary transition: Dec 31 (Day 366) to Jan 1 of next year
            val promptJan1 = PromptRotationEngine.promptForDate(LocalDate.of(year + 1, 1, 1), customOffset = 0)
            assertNotEquals(
                "Dec 31 Day 366 to Jan 1 must not yield duplicate prompt ID",
                promptDay366.id,
                promptJan1.id
            )
        }
    }

    @Test
    fun testCategoryFilteringStrictAdherenceAcrossEntireDecade() {
        val testDates = listOf(
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2024, 12, 31),
            LocalDate.of(2025, 6, 15),
            LocalDate.of(2026, 10, 27),
            LocalDate.of(2028, 2, 29),
            LocalDate.of(2030, 8, 14),
            LocalDate.of(2033, 12, 31)
        )

        PromptCategory.values().forEach { category ->
            for (date in testDates) {
                for (offset in -3..3) {
                    val prompt = PromptRotationEngine.promptForDateAndCategory(date, category, customOffset = offset)
                    assertEquals(
                        "Prompt for date $date with category ${category.name} and offset $offset must match category",
                        category,
                        prompt.category
                    )
                }

                // Verify consecutive days in category do not duplicate
                val today = PromptRotationEngine.promptForDateAndCategory(date, category, customOffset = 0)
                val tomorrow = PromptRotationEngine.promptForDateAndCategory(date.plusDays(1), category, customOffset = 0)
                assertNotEquals(
                    "Consecutive days within category ${category.name} ($date vs ${date.plusDays(1)}) must not be identical",
                    today.id,
                    tomorrow.id
                )
            }
        }
    }

    @Test
    fun testShuffleOffsetsAdversarialStressAndWrapping() {
        val testDate = LocalDate.of(2026, 10, 27)

        val adversarialOffsets = listOf(
            0, 1, 2, 5, 31, 319, 320, 321, 640, 10_000, 100_000, 1_000_000,
            Int.MAX_VALUE - 1, Int.MAX_VALUE,
            -1, -2, -5, -31, -319, -320, -321, -640, -10_000, -100_000, -1_000_000,
            Int.MIN_VALUE + 1, Int.MIN_VALUE
        )

        adversarialOffsets.forEach { offset ->
            // Base prompt rotation under extreme offset
            val prompt = try {
                PromptRotationEngine.promptForDate(testDate, customOffset = offset)
            } catch (t: Throwable) {
                fail("promptForDate crashed with offset $offset: ${t.message}")
                throw t
            }
            assertNotNull("Prompt must not be null for offset $offset", prompt)
            assertTrue("Prompt text must not be blank for offset $offset", prompt.text.isNotBlank())

            // Category prompt rotation under extreme offset
            PromptCategory.values().forEach { category ->
                val catPrompt = try {
                    PromptRotationEngine.promptForDateAndCategory(testDate, category, customOffset = offset)
                } catch (t: Throwable) {
                    fail("promptForDateAndCategory crashed with offset $offset, category ${category.name}: ${t.message}")
                    throw t
                }
                assertNotNull("Category prompt must not be null for offset $offset", catPrompt)
                assertEquals(
                    "Category must strictly match even under extreme offset $offset",
                    category,
                    catPrompt.category
                )
            }
        }
    }

    @Test
    fun testConsecutiveShuffleOffsetsYieldDifferentPrompts() {
        val date = LocalDate.of(2026, 10, 27)

        // Stepping shuffle offset from 0 to 50: adjacent offsets must rotate prompts
        for (offset in 0 until 50) {
            val current = PromptRotationEngine.promptForDate(date, customOffset = offset)
            val next = PromptRotationEngine.promptForDate(date, customOffset = offset + 1)
            assertNotEquals(
                "Consecutive shuffle offsets ($offset and ${offset + 1}) must yield different prompts",
                current.id,
                next.id
            )
        }
    }

    // -------------------------------------------------------------------------
    // 3. AppLockManager Security Boundary Preservation
    // -------------------------------------------------------------------------

    @Test
    fun testAppLockBoundaryPreservationWithPrompts() {
        // AppLock security rule: when app lock is enabled and session is not authenticated,
        // private journal content must be masked.
        assertTrue(
            "When App Lock is enabled and user is unauthenticated, content must be masked",
            AppLockSecurityEngine.shouldMaskContent(isAppLockEnabled = true, isSessionAuthenticated = false)
        )

        // When authenticated, diary content is accessible
        assertFalse(
            "When App Lock is enabled and user is authenticated, content must not be masked",
            AppLockSecurityEngine.shouldMaskContent(isAppLockEnabled = true, isSessionAuthenticated = true)
        )

        // When App Lock is disabled, content is accessible
        assertFalse(
            "When App Lock is disabled, content must not be masked",
            AppLockSecurityEngine.shouldMaskContent(isAppLockEnabled = false, isSessionAuthenticated = false)
        )

        // Verify that prompt catalog text is purely public/generic and does not contain private user tokens
        BundledPromptCatalog.ALL_PROMPTS.forEach { prompt ->
            assertFalse(
                "Prompt '${prompt.id}' text must not contain user private placeholder markers",
                prompt.text.contains("{{USER_") || prompt.text.contains("SECRET")
            )
        }
    }

    // -------------------------------------------------------------------------
    // 4. Adversarial Catalog Lookup by ID
    // -------------------------------------------------------------------------

    @Test
    fun testAdversarialCatalogLookupById() {
        // Valid prompts lookup
        BundledPromptCatalog.ALL_PROMPTS.take(20).forEach { prompt ->
            val found = PromptRotationEngine.getPromptById(prompt.id)
            assertNotNull("Catalog prompt ${prompt.id} must be retrievable by ID", found)
            assertEquals(prompt.id, found?.id)
            assertEquals(prompt.text, found?.text)
        }

        // Dedicated leap prompt lookup
        val leapFound = PromptRotationEngine.getPromptById("prompt_leap_day_quadrennial")
        assertNotNull(leapFound)
        assertEquals("prompt_leap_day_quadrennial", leapFound?.id)

        // Malformed, blank, and injection IDs
        val adversarialIds = listOf(
            "",
            "   ",
            "\n\t",
            "null",
            "prompt_gratitude_9999",
            "prompt_invalid_category_1",
            "'; DROP TABLE journal_entries; --",
            "../../../etc/passwd",
            "<script>alert('xss')</script>",
            "\u0000\u0001\uFFFF",
            "prompt_leap_day_quadrennial_extra"
        )

        adversarialIds.forEach { malformedId ->
            val result = PromptRotationEngine.getPromptById(malformedId)
            assertNull("Lookup for malformed ID '$malformedId' must return null without throwing", result)
        }
    }
}
