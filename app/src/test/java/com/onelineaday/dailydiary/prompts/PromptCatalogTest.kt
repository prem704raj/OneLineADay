package com.onelineaday.dailydiary.prompts

import org.junit.Assert.*
import org.junit.Test

class PromptCatalogTest {

    @Test
    fun `catalog contains exactly 320 bundled prompts across 10 categories`() {
        val catalog = BundledPromptCatalog.ALL_PROMPTS
        assertEquals(320, catalog.size)
        assertEquals(10, PromptCategory.values().size)
    }

    @Test
    fun `every category contains exactly 32 unique prompts`() {
        PromptCategory.values().forEach { category ->
            val prompts = BundledPromptCatalog.ALL_PROMPTS.filter { it.category == category }
            assertEquals("Category ${category.name} must have 32 prompts", 32, prompts.size)

            val uniqueIds = prompts.map { it.id }.toSet()
            assertEquals("All prompt IDs in category ${category.name} must be unique", 32, uniqueIds.size)
        }
    }

    @Test
    fun `all prompt IDs across entire catalog are distinct`() {
        val allIds = BundledPromptCatalog.ALL_PROMPTS.map { it.id }
        assertEquals(320, allIds.distinct().size)
    }

    @Test
    fun `no prompt has blank or whitespace-only text`() {
        BundledPromptCatalog.ALL_PROMPTS.forEach { prompt ->
            assertTrue("Prompt text for ID ${prompt.id} must not be blank", prompt.text.isNotBlank())
            assertTrue("Prompt ID ${prompt.id} must be properly formatted", prompt.id.startsWith("prompt_"))
        }
    }

    @Test
    fun `dedicated quadrennial leap day prompt has correct attributes`() {
        val leapPrompt = BundledPromptCatalog.LEAP_DAY_PROMPT
        assertEquals("prompt_leap_day_quadrennial", leapPrompt.id)
        assertEquals(PromptCategory.REFLECTION, leapPrompt.category)
        assertTrue(leapPrompt.text.isNotBlank())
        assertTrue(leapPrompt.text.contains("bonus hours", ignoreCase = true))
    }
}
