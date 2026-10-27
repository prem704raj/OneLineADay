package com.onelineaday.dailydiary.prompts

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * Manages local preferences for Daily Spark Prompts:
 * - Answered prompt status tracking.
 * - Per-day shuffle offsets.
 * - Active category filter preference.
 */
class PromptPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("daily_spark_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ANSWERED_IDS = "answered_prompt_ids"
        private const val PREFIX_DATE_PROMPT = "date_prompt_"
        private const val PREFIX_SHUFFLE_OFFSET = "shuffle_offset_"
        private const val KEY_SELECTED_CATEGORY = "selected_category_filter"
    }

    /**
     * Marks [promptId] as answered for [date].
     */
    fun markPromptAnswered(date: LocalDate, promptId: String) {
        val currentIds = getAnsweredPromptIds().toMutableSet()
        currentIds.add(promptId)
        prefs.edit()
            .putStringSet(KEY_ANSWERED_IDS, currentIds)
            .putString(PREFIX_DATE_PROMPT + date.toString(), promptId)
            .apply()
    }

    /**
     * Unmarks the prompt associated with [date].
     */
    fun unmarkPromptAnswered(date: LocalDate) {
        val promptId = getAnsweredPromptForDate(date)
        val editor = prefs.edit().remove(PREFIX_DATE_PROMPT + date.toString())
        if (promptId != null) {
            val stillAnsweredElsewhere = prefs.all.entries.any {
                it.key.startsWith(PREFIX_DATE_PROMPT) &&
                it.key != (PREFIX_DATE_PROMPT + date.toString()) &&
                it.value == promptId
            }
            if (!stillAnsweredElsewhere) {
                val currentIds = getAnsweredPromptIds().toMutableSet()
                currentIds.remove(promptId)
                editor.putStringSet(KEY_ANSWERED_IDS, currentIds)
            }
        }
        editor.apply()
    }

    /**
     * Checks if a prompt with [promptId] has ever been answered.
     */
    fun isPromptAnswered(promptId: String): Boolean {
        return getAnsweredPromptIds().contains(promptId)
    }

    /**
     * Checks if an answered prompt is recorded specifically for [date].
     */
    fun isDatePromptAnswered(date: LocalDate): Boolean {
        return prefs.contains(PREFIX_DATE_PROMPT + date.toString())
    }

    /**
     * Gets the answered prompt ID for [date], or null if none.
     */
    fun getAnsweredPromptForDate(date: LocalDate): String? {
        return prefs.getString(PREFIX_DATE_PROMPT + date.toString(), null)
    }

    /**
     * Gets the set of all answered prompt IDs.
     */
    fun getAnsweredPromptIds(): Set<String> {
        return prefs.getStringSet(KEY_ANSWERED_IDS, emptySet()) ?: emptySet()
    }

    /**
     * Gets the custom shuffle offset for [date].
     */
    fun getShuffleOffset(date: LocalDate): Int {
        return prefs.getInt(PREFIX_SHUFFLE_OFFSET + date.toString(), 0)
    }

    /**
     * Sets the custom shuffle offset for [date].
     */
    fun setShuffleOffset(date: LocalDate, offset: Int) {
        prefs.edit().putInt(PREFIX_SHUFFLE_OFFSET + date.toString(), offset).apply()
    }

    /**
     * Increments and returns the new shuffle offset for [date].
     */
    fun incrementShuffleOffset(date: LocalDate): Int {
        val newOffset = getShuffleOffset(date) + 1
        setShuffleOffset(date, newOffset)
        return newOffset
    }

    /**
     * Gets the currently selected category filter, or null if all categories.
     */
    fun getSelectedCategoryFilter(): PromptCategory? {
        val name = prefs.getString(KEY_SELECTED_CATEGORY, null) ?: return null
        return runCatching { PromptCategory.valueOf(name) }.getOrNull()
    }

    /**
     * Sets or clears the active category filter.
     */
    fun setSelectedCategoryFilter(category: PromptCategory?) {
        if (category == null) {
            prefs.edit().remove(KEY_SELECTED_CATEGORY).apply()
        } else {
            prefs.edit().putString(KEY_SELECTED_CATEGORY, category.name).apply()
        }
    }
}
