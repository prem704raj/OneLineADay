package com.onelineaday.dailydiary.constellation

import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

data class StarNode(
    val entry: JournalEntry,
    val x: Float, // 0.0f to 1.0f
    val y: Float, // 0.0f to 1.0f
    val brightness: Float, // 0.5f to 1.0f
    val radius: Float // 4f to 12f
)

data class ConstellationEdge(
    val from: StarNode,
    val to: StarNode,
    val reason: String,
    val opacity: Float = 0.4f
)

data class ConstellationGraph(
    val stars: List<StarNode>,
    val edges: List<ConstellationEdge>
)

object MemoryConstellationEngine {

    /**
     * Builds a celestial constellation star map from journal entries.
     */
    fun buildConstellation(entries: List<JournalEntry>): ConstellationGraph {
        if (entries.isEmpty()) return ConstellationGraph(emptyList(), emptyList())

        val sorted = entries.sortedBy { it.date }
        val count = sorted.size

        // Position stars across a spiral celestial layout
        val stars = sorted.mapIndexed { index, entry ->
            val angle = index.toDouble() * 2.39996 // Golden ratio spiral angle in radians
            val radius = 0.15 + (index.toDouble() / count.toDouble()) * 0.32
            val x = (0.5 + radius * cos(angle)).toFloat().coerceIn(0.08f, 0.92f)
            val y = (0.5 + radius * sin(angle)).toFloat().coerceIn(0.08f, 0.92f)

            val hasMedia = entry.photoUri != null || entry.mediaUris.isNotEmpty()
            val starRadius = if (hasMedia) 10f else if (entry.content.length > 80) 8f else 5f
            val brightness = when (entry.mood) {
                Mood.EXCITED, Mood.LOVED -> 1.0f
                Mood.HAPPY, Mood.GRATEFUL, Mood.MOTIVATED -> 0.85f
                Mood.NEUTRAL, Mood.TIRED -> 0.65f
                Mood.ANXIOUS, Mood.SAD, Mood.ANGRY -> 0.5f
            }

            StarNode(entry, x, y, brightness, starRadius)
        }

        // Build edges between stars that share tags or consecutive days
        val edges = mutableListOf<ConstellationEdge>()
        val tagToStars = mutableMapOf<String, MutableList<StarNode>>()

        for (star in stars) {
            for (tag in star.entry.tags) {
                val clean = tag.trim().lowercase()
                if (clean.isNotBlank()) {
                    tagToStars.getOrPut(clean) { mutableListOf() }.add(star)
                }
            }
        }

        // Connect stars sharing common tags
        for ((tag, starList) in tagToStars) {
            if (starList.size in 2..6) {
                for (i in 0 until starList.size - 1) {
                    edges.add(ConstellationEdge(starList[i], starList[i + 1], "Shared tag #$tag", 0.5f))
                }
            }
        }

        return ConstellationGraph(stars, edges)
    }
}

