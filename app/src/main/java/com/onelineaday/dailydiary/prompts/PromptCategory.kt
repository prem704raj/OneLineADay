package com.onelineaday.dailydiary.prompts

/**
 * 10 distinct prompt categories balancing gratitude, personal development,
 * emotional clarity, relationships, creativity, mindfulness, and joy.
 */
enum class PromptCategory(
    val displayName: String,
    val description: String,
    val colorHex: Long
) {
    GRATITUDE(
        displayName = "Gratitude",
        description = "Appreciating people, simple comforts, and unexpected gifts in life",
        colorHex = 0xFFFFB347 // SunsetAmber
    ),
    SMALL_WINS(
        displayName = "Small Wins",
        description = "Celebrating minor victories, micro-progress, and quiet perseverance",
        colorHex = 0xFF4ECDC4 // AccentTeal
    ),
    GROWTH(
        displayName = "Personal Growth",
        description = "Learning from mistakes, expanding wisdom, and embracing curiosity",
        colorHex = 0xFF4CAF50 // Emerald Green
    ),
    REFLECTION(
        displayName = "Deep Reflection",
        description = "Exploring core values, personal boundaries, and inner truth",
        colorHex = 0xFFB8A9C9 // LavenderMid
    ),
    RELATIONSHIPS(
        displayName = "Relationships",
        description = "Connecting with loved ones, friends, mentors, and community",
        colorHex = 0xFFFF6B6B // SunsetRose
    ),
    FUTURE(
        displayName = "Future Self",
        description = "Envisioning goals, dreams, aspirations, and letters to future self",
        colorHex = 0xFF5C6BC0 // Indigo Horizon
    ),
    CREATIVITY(
        displayName = "Creativity",
        description = "Unlocking imagination, playfulness, novel ideas, and artistic wonder",
        colorHex = 0xFFAA66FF // ExcitedPurple
    ),
    MINDFULNESS(
        displayName = "Mindfulness",
        description = "Anchoring in the sensory present, stillness, breath, and awareness",
        colorHex = 0xFF26A69A // Calm Sage
    ),
    CHALLENGES(
        displayName = "Daily Challenges",
        description = "Navigating friction, emotional resilience, and overcoming adversity",
        colorHex = 0xFFFF8C42 // SunsetOrange
    ),
    JOY(
        displayName = "Pure Joy",
        description = "Embracing laughter, delight, playful moments, and simple pleasures",
        colorHex = 0xFFFFD93D // HappyYellow
    )
}
