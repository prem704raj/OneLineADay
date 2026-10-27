package com.onelineaday.dailydiary.search

import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import java.time.Month
import java.util.Locale

data class SearchResultMatch(
    val entry: JournalEntry,
    val score: Double,
    val matchReasons: List<String>
)

object SmartSearchEngine {

    private val POSITIVE_KEYWORDS = setOf("happy", "happiest", "joy", "excited", "grateful", "best day", "celebrate")
    private val SAD_KEYWORDS = setOf("sad", "toughest", "difficult", "tired", "crying", "grief", "hard day")

    /**
     * Executes privacy-first smart ranking search across local journal entries.
     */
    fun search(entries: List<JournalEntry>, query: String): List<SearchResultMatch> {
        val trimmed = query.trim().lowercase(Locale.ROOT)
        if (trimmed.isBlank()) return emptyList()

        val tokens = trimmed.split(Regex("\\W+")).filter { it.isNotBlank() }

        // Detect mood intent
        val seeksPositive = tokens.any { it in POSITIVE_KEYWORDS }
        val seeksSad = tokens.any { it in SAD_KEYWORDS }

        // Detect month filter
        val detectedMonth = Month.values().firstOrNull { month ->
            tokens.contains(month.name.lowercase(Locale.ROOT))
        }

        // Detect media filter ("photo", "picture", "image")
        val seeksMedia = tokens.any { it in setOf("photo", "photos", "picture", "pictures", "image", "images") }

        val matches = mutableListOf<SearchResultMatch>()

        for (entry in entries) {
            var score = 0.0
            val reasons = mutableListOf<String>()

            val contentLower = entry.content.lowercase(Locale.ROOT)
            val tagsLower = entry.tags.map { it.lowercase(Locale.ROOT) }

            // 1. Exact phrase match
            if (contentLower.contains(trimmed)) {
                score += 20.0
                reasons.add("Exact phrase match")
            }

            // 2. Token match
            var matchedTokenCount = 0
            for (token in tokens) {
                if (contentLower.contains(token)) {
                    score += 5.0
                    matchedTokenCount++
                }
                if (tagsLower.any { it.contains(token) }) {
                    score += 8.0
                    reasons.add("Matches tag #$token")
                    matchedTokenCount++
                }
            }

            // 3. Mood semantic intent
            if (seeksPositive && entry.mood in setOf(Mood.HAPPY, Mood.EXCITED, Mood.GRATEFUL, Mood.LOVED)) {
                score += 12.0
                reasons.add("Matches positive mood intent (${entry.mood.emoji})")
            } else if (seeksSad && entry.mood in setOf(Mood.SAD, Mood.ANGRY, Mood.TIRED, Mood.ANXIOUS)) {
                score += 12.0
                reasons.add("Matches reflective/low mood intent (${entry.mood.emoji})")
            }

            // 4. Month filter
            if (detectedMonth != null && entry.date.month == detectedMonth) {
                score += 10.0
                reasons.add("Recorded in ${detectedMonth.name.lowercase(Locale.ROOT).capitalize(Locale.ROOT)}")
            }

            // 5. Media filter
            if (seeksMedia && (entry.photoUri != null || entry.mediaUris.isNotEmpty())) {
                score += 10.0
                reasons.add("Contains photo attachment")
            }

            if (score > 0.0) {
                matches.add(SearchResultMatch(entry, score, reasons))
            }
        }

        return matches.sortedByDescending { it.score }
    }
}

