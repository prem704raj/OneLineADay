package com.onelineaday.dailydiary.prompts

import java.time.LocalDate

/**
 * Deterministic prompt rotation engine.
 * Computes calendar-based daily prompt assignments with zero network dependencies.
 */
object PromptRotationEngine {

    val CATALOG: List<DailySparkPrompt> = BundledPromptCatalog.ALL_PROMPTS

    val LEAP_DAY_PROMPT: DailySparkPrompt = BundledPromptCatalog.LEAP_DAY_PROMPT

    /**
     * Deterministically retrieves a prompt for a given calendar date.
     * Guaranteed to return the exact same prompt for the same date regardless of run time.
     */
    fun promptForDate(date: LocalDate, customOffset: Int = 0): DailySparkPrompt {
        // Dedicated Quadrennial Leap Day prompt (Feb 29) when un-shuffled
        if (date.monthValue == 2 && date.dayOfMonth == 29 && customOffset == 0) {
            return LEAP_DAY_PROMPT
        }

        val dayOfYear = date.dayOfYear // 1..366
        val yearSeed = date.year
        // Deterministic hash combining date components
        val index = ((dayOfYear * 31 + yearSeed * 17 + customOffset) and 0x7FFFFFFF) % CATALOG.size
        return CATALOG[index]
    }

    /**
     * Deterministically retrieves a prompt restricted to a specific [category].
     */
    fun promptForDateAndCategory(
        date: LocalDate,
        category: PromptCategory,
        customOffset: Int = 0
    ): DailySparkPrompt {
        val categoryPrompts = promptsForCategory(category)
        if (categoryPrompts.isEmpty()) return promptForDate(date, customOffset)

        val dayOfYear = date.dayOfYear
        val yearSeed = date.year
        val index = ((dayOfYear * 13 + yearSeed * 7 + customOffset) and 0x7FFFFFFF) % categoryPrompts.size
        return categoryPrompts[index]
    }

    /**
     * Filter catalog by category.
     */
    fun promptsForCategory(category: PromptCategory): List<DailySparkPrompt> {
        return CATALOG.filter { it.category == category }
    }

    /**
     * Look up a prompt by its identifier.
     */
    fun getPromptById(id: String): DailySparkPrompt? {
        if (id == LEAP_DAY_PROMPT.id) return LEAP_DAY_PROMPT
        return CATALOG.firstOrNull { it.id == id }
    }
}
