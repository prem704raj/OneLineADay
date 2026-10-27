package com.onelineaday.dailydiary.prompts

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PromptRotationEngineTest {

    @Test
    fun `deterministic daily rotation returns identical prompt for same calendar date`() {
        val date = LocalDate.of(2026, 10, 27)
        val prompt1 = PromptRotationEngine.promptForDate(date)
        val prompt2 = PromptRotationEngine.promptForDate(date)

        assertEquals(prompt1.id, prompt2.id)
        assertEquals(prompt1.text, prompt2.text)
        assertEquals(prompt1.category, prompt2.category)
    }

    @Test
    fun `consecutive dates rotate to different prompt IDs across categories`() {
        val date1 = LocalDate.of(2026, 10, 27)
        val date2 = LocalDate.of(2026, 10, 28)

        val prompt1 = PromptRotationEngine.promptForDate(date1)
        val prompt2 = PromptRotationEngine.promptForDate(date2)

        assertNotEquals(prompt1.id, prompt2.id)
    }

    @Test
    fun `full year 365 days traversal covers all 10 categories`() {
        val startDate = LocalDate.of(2025, 1, 1)
        val coveredCategories = mutableSetOf<PromptCategory>()

        for (day in 0 until 365) {
            val date = startDate.plusDays(day.toLong())
            val prompt = PromptRotationEngine.promptForDate(date)
            coveredCategories.add(prompt.category)
        }

        assertEquals("All 10 categories must be covered in 365 days", 10, coveredCategories.size)
    }

    @Test
    fun `leap day Feb 29 returns dedicated leap prompt on offset zero and catalog prompt on shuffle`() {
        val leapDay = LocalDate.of(2024, 2, 29)
        val prompt = PromptRotationEngine.promptForDate(leapDay, customOffset = 0)

        assertEquals("prompt_leap_day_quadrennial", prompt.id)
        assertEquals(PromptCategory.REFLECTION, prompt.category)

        // Shuffled offset on Feb 29
        val shuffledPrompt = PromptRotationEngine.promptForDate(leapDay, customOffset = 1)
        assertNotNull(shuffledPrompt)
        assertTrue(shuffledPrompt.text.isNotBlank())
    }

    @Test
    fun `day 366 Dec 31 in leap year executes cleanly without exception`() {
        val day366 = LocalDate.of(2024, 12, 31)
        assertEquals(366, day366.dayOfYear)

        val prompt = PromptRotationEngine.promptForDate(day366)
        assertNotNull(prompt)
        assertTrue(prompt.text.isNotBlank())
    }

    @Test
    fun `category-restricted rotation guarantees returned prompts match category`() {
        val date = LocalDate.of(2026, 7, 4)

        PromptCategory.values().forEach { category ->
            for (offset in 0..5) {
                val prompt = PromptRotationEngine.promptForDateAndCategory(date, category, offset)
                assertEquals(
                    "Prompt $prompt must belong to category $category",
                    category,
                    prompt.category
                )
            }
        }
    }

    @Test
    fun `custom shuffle offset rotates prompt`() {
        val date = LocalDate.of(2026, 8, 15)
        val basePrompt = PromptRotationEngine.promptForDate(date, customOffset = 0)
        val shuffledPrompt = PromptRotationEngine.promptForDate(date, customOffset = 1)

        assertNotEquals(basePrompt.id, shuffledPrompt.id)
    }

    @Test
    fun `getPromptById retrieves catalog prompts and leap prompt`() {
        val leap = PromptRotationEngine.getPromptById("prompt_leap_day_quadrennial")
        assertNotNull(leap)
        assertEquals("prompt_leap_day_quadrennial", leap?.id)

        val gratitude = PromptRotationEngine.getPromptById("prompt_gratitude_1")
        assertNotNull(gratitude)
        assertEquals(PromptCategory.GRATITUDE, gratitude?.category)

        val nonexistent = PromptRotationEngine.getPromptById("prompt_nonexistent_999")
        assertNull(nonexistent)
    }
}
