package com.onelineaday.dailydiary.ai.distill

import java.util.Locale

object LocalTextDistillationEngine {

    private val FILLER_WORDS = setOf(
        "um", "uh", "like", "you know", "basically", "literally", "actually",
        "kind of", "sort of", "i guess", "i mean", "so yeah"
    )

    private val HIGH_SALIENCE_KEYWORDS = setOf(
        "grateful", "gratitude", "happy", "proud", "peaceful", "loved", "excited",
        "finally", "finished", "achieved", "completed", "learned", "realized",
        "discovered", "decided", "celebrated", "began", "started", "conquered",
        "breakthrough", "milestone", "overcame", "miracle", "wonder", "alive"
    )

    private val SENTENCE_DELIMITERS = Regex("[.!?\\n]+")

    /**
     * Distills a raw text paragraph or multi-sentence voice transcript into a single concise line.
     * Guaranteed to be purely local, deterministic, and fast ($O(N)$).
     */
    fun distillToSingleLine(rawText: String, maxLength: Int = 140): String {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return ""

        // Clean filler words
        var cleaned = trimmed
        for (filler in FILLER_WORDS) {
            val regex = Regex("(?i)\\b$filler\\b[ ,]?")
            cleaned = cleaned.replace(regex, "")
        }
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        // Split into candidate sentences
        val sentences = cleaned.split(SENTENCE_DELIMITERS)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (sentences.isEmpty()) return ""

        if (sentences.size == 1) {
            return truncateNaturally(sentences.first(), maxLength)
        }

        // Score sentences by emotional salience, length appropriateness, and climax
        var bestSentence = sentences.first()
        var maxScore = Double.NEGATIVE_INFINITY

        for ((index, sentence) in sentences.withIndex()) {
            var score = 0.0
            val lower = sentence.lowercase(Locale.ROOT)
            val words = lower.split(Regex("\\W+")).filter { it.isNotBlank() }

            // Salience keywords
            for (word in words) {
                if (word in HIGH_SALIENCE_KEYWORDS) {
                    score += 5.0
                }
            }

            // Climax bias: Later sentences in a daily journal often contain the reflection or conclusion
            val positionBonus = (index.toDouble() / sentences.size.toDouble()) * 2.0
            score += positionBonus

            // Ideal word count bonus (between 6 and 18 words)
            val wordCount = words.size
            if (wordCount in 6..18) {
                score += 3.0
            } else if (wordCount < 4) {
                score -= 2.0
            }

            if (score > maxScore) {
                maxScore = score
                bestSentence = sentence
            }
        }

        return truncateNaturally(bestSentence, maxLength)
    }

    private fun truncateNaturally(text: String, maxLength: Int): String {
        var result = text.trim()

        // Ensure first letter is capitalized
        if (result.isNotEmpty() && result[0].isLowerCase()) {
            result = result.replaceFirstChar { it.uppercaseChar() }
        }

        if (result.length <= maxLength) {
            // Ensure proper terminal punctuation
            if (!result.endsWith('.') && !result.endsWith('!') && !result.endsWith('?')) {
                result += "."
            }
            return result
        }

        // Truncate at word boundary
        val truncated = result.substring(0, maxLength).trimEnd()
        val lastSpace = truncated.lastIndexOf(' ')
        val safeText = if (lastSpace > maxLength / 2) {
            truncated.substring(0, lastSpace)
        } else {
            truncated
        }

        return safeText.trimEnd(',', ';', ':', '-', ' ') + "…"
    }
}

