package com.onelineaday.dailydiary.prompts

/**
 * Immutable representation of a single daily spark journaling prompt.
 */
data class DailySparkPrompt(
    val id: String,
    val text: String,
    val category: PromptCategory
)
