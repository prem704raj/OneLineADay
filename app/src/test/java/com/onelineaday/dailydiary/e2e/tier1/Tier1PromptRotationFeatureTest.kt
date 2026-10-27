package com.onelineaday.dailydiary.e2e.tier1

import com.onelineaday.dailydiary.e2e.engine.PromptCategory
import com.onelineaday.dailydiary.e2e.engine.PromptRotationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tier 1: Core Feature Coverage for Daily Spark Prompt Catalog & Rotation (F10, F11).
 */
class Tier1PromptRotationFeatureTest {

    @Test
    fun `catalog contains at least 300 bundled prompts across 10 categories`() {
        val catalog = PromptRotationEngine.CATALOG
        assertTrue("Catalog must contain at least 300 prompts (found ${catalog.size})", catalog.size >= 300)
        assertEquals("Must span all 10 PromptCategory values", 10, PromptCategory.values().size)
    }

    @Test
    fun `every category contains at least 30 unique prompts`() {
        PromptCategory.values().forEach { category ->
            val prompts = PromptRotationEngine.promptsForCategory(category)
            assertTrue("Category ${category.name} must have >= 30 prompts", prompts.size >= 30)
            val uniqueIds = prompts.map { it.id }.toSet()
            assertEquals("Prompt IDs must be unique within category", prompts.size, uniqueIds.size)
        }
    }

    @Test
    fun `deterministic daily rotation returns identical prompt for same calendar date`() {
        val date = LocalDate.of(2026, 6, 15)
        val prompt1 = PromptRotationEngine.promptForDate(date)
        val prompt2 = PromptRotationEngine.promptForDate(date)

        assertEquals("Same date must yield identical prompt ID", prompt1.id, prompt2.id)
        assertEquals("Same date must yield identical prompt text", prompt1.text, prompt2.text)
        assertEquals("Same date must yield identical category", prompt1.category, prompt2.category)
    }

    @Test
    fun `consecutive dates rotate to different prompts`() {
        val date1 = LocalDate.of(2026, 6, 15)
        val date2 = LocalDate.of(2026, 6, 16)

        val prompt1 = PromptRotationEngine.promptForDate(date1)
        val prompt2 = PromptRotationEngine.promptForDate(date2)

        assertNotEquals("Consecutive dates must not yield the same prompt ID", prompt1.id, prompt2.id)
    }

    @Test
    fun `leap day day 366 returns valid non-null prompt`() {
        val leapDay = LocalDate.of(2024, 2, 29)
        val prompt = PromptRotationEngine.promptForDate(leapDay)
        assertNotNull(prompt)
        assertTrue(prompt.text.isNotBlank())
        assertTrue(prompt.id.isNotBlank())
    }

    @Test
    fun `custom offset cleanly rotates prompt without crashing`() {
        val date = LocalDate.of(2026, 1, 1)
        val promptDefault = PromptRotationEngine.promptForDate(date, customOffset = 0)
        val promptOffset = PromptRotationEngine.promptForDate(date, customOffset = 1)

        assertNotEquals("Custom offset must vary the prompt returned", promptDefault.id, promptOffset.id)
    }
}
