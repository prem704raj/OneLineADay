package com.onelineaday.dailydiary.zen

data class ZenItem(
    val id: String,
    val name: String,
    val description: String,
    val requiredStreak: Int,
    val isUnlocked: Boolean,
    val emoji: String
)

data class ZenGardenState(
    val currentStreak: Int,
    val longestStreak: Int,
    val unlockedItems: List<ZenItem>,
    val nextItemToUnlock: ZenItem?,
    val progressToNext: Float // 0.0f to 1.0f
)

object ZenGardenEngine {

    val CATALOG = listOf(
        ZenItem("stone", "Smooth River Stones", "Balanced and grounded amidst daily currents.", 3, false, "🪨"),
        ZenItem("bamboo", "Green Bamboo Grove", "Bends gracefully with the wind without breaking.", 7, false, "🎋"),
        ZenItem("koi", "Tranquil Koi Pond", "Swimming gently in ripples of quiet contemplation.", 14, false, "🎏"),
        ZenItem("blossom", "Cherry Blossom Tree", "Ephemeral beauty blooming through mindful attention.", 30, false, "🌸"),
        ZenItem("lantern", "Stone Lantern", "Casting a calm, warm glow over months of memories.", 60, false, "🏮"),
        ZenItem("pagoda", "Golden Pagoda", "The pinnacle of unbroken consistency and inner peace.", 100, false, "⛩️")
    )

    fun computeState(currentStreak: Int, longestStreak: Int): ZenGardenState {
        val effectiveStreak = maxOf(currentStreak, longestStreak)

        val items = CATALOG.map { item ->
            item.copy(isUnlocked = effectiveStreak >= item.requiredStreak)
        }

        val unlocked = items.filter { it.isUnlocked }
        val next = items.firstOrNull { !it.isUnlocked }

        val progress = if (next == null) {
            1.0f
        } else {
            val previousReq = CATALOG.indexOf(next).let { if (it > 0) CATALOG[it - 1].requiredStreak else 0 }
            val range = (next.requiredStreak - previousReq).coerceAtLeast(1)
            val currentProgress = (effectiveStreak - previousReq).coerceAtLeast(0)
            (currentProgress.toFloat() / range.toFloat()).coerceIn(0f, 1f)
        }

        return ZenGardenState(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            unlockedItems = unlocked,
            nextItemToUnlock = next,
            progressToNext = progress
        )
    }
}

