package com.onelineaday.dailydiary.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.ui.components.*
import com.onelineaday.dailydiary.ui.theme.*
import com.onelineaday.dailydiary.ads.BannerAdView
import com.onelineaday.dailydiary.viewmodel.JournalUiState
import com.onelineaday.dailydiary.viewmodel.JournalViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: JournalViewModel,
    uiState: JournalUiState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    // Calculate additional stats
    val firstEntryDate = uiState.entries.minByOrNull { it.date }?.date
    val journeyDays = firstEntryDate?.let { 
        ChronoUnit.DAYS.between(it, LocalDate.now()).toInt() + 1 
    } ?: 0
    
    val thisMonthEntries = uiState.entries.filter { 
        it.date.month == LocalDate.now().month && 
        it.date.year == LocalDate.now().year 
    }.size
    
    val thisWeekEntries = uiState.entries.filter { entry ->
        val today = LocalDate.now()
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        entry.date >= weekStart && entry.date <= today
    }.size
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.stats_your_journey),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Streak Card (prominent)
                StreakCard(
                    currentStreak = uiState.currentStreak,
                    longestStreak = uiState.longestStreak
                )
                
                // Year in Review
                YearInReviewCard(
                    entries = uiState.entries
                )
                
                // Stats Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsCard(
                        title = stringResource(R.string.total_entries),
                        value = uiState.totalEntries.toString(),
                        subtitle = stringResource(R.string.stats_memories_captured),
                        icon = Icons.Rounded.Book,
                        gradientColors = listOf(AccentTeal, AccentTeal.copy(blue = 0.6f)),
                        modifier = Modifier.weight(1f)
                    )
                    
                    StatsCard(
                        title = stringResource(R.string.stats_journey),
                        value = journeyDays.toString(),
                        subtitle = stringResource(R.string.stats_days_since_start),
                        icon = Icons.Rounded.Timeline,
                        gradientColors = listOf(LavenderMid, LavenderDark),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Weekly & Monthly Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsCard(
                        title = stringResource(R.string.stats_this_week),
                        value = thisWeekEntries.toString(),
                        subtitle = stringResource(R.string.stats_days_left, 7 - thisWeekEntries),
                        icon = Icons.Rounded.DateRange,
                        gradientColors = listOf(MoodMotivated, MoodMotivated.copy(green = 0.6f)),
                        modifier = Modifier.weight(1f)
                    )
                    
                    StatsCard(
                        title = stringResource(R.string.stats_this_month),
                        value = thisMonthEntries.toString(),
                        subtitle = stringResource(R.string.stats_entries),
                        icon = Icons.Rounded.CalendarMonth,
                        gradientColors = listOf(SunsetRose, SunsetPink),
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Mood Distribution
                MoodDistributionCard(
                    moodDistribution = uiState.moodDistribution
                )
                
                // Milestones Section
                MilestonesCard(
                    totalEntries = uiState.totalEntries,
                    currentStreak = uiState.currentStreak,
                    journeyDays = journeyDays
                )
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            BannerAdView()
        }
    }
}

@Composable
fun MilestonesCard(
    totalEntries: Int,
    currentStreak: Int,
    journeyDays: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.stats_milestones),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Entry milestones
            val entryMilestones = listOf(
                Triple(7, stringResource(R.string.stats_first_week), "📅"),
                Triple(30, stringResource(R.string.stats_one_month), "📆"),
                Triple(100, stringResource(R.string.stats_century_club), "💯"),
                Triple(365, stringResource(R.string.stats_one_year), "🎉"),
                Triple(1000, stringResource(R.string.stats_legendary), "🏆")
            )
            
            entryMilestones.forEach { (target, label, emoji) ->
                MilestoneItem(
                    emoji = emoji,
                    label = label,
                    current = totalEntries,
                    target = target,
                    isCompleted = totalEntries >= target
                )
                
                if (target != 1000) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun MilestoneItem(
    emoji: String,
    label: String,
    current: Int,
    target: Int,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = (current.toFloat() / target).coerceIn(0f, 1f)
    
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleLarge
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCompleted) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCompleted) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = if (isCompleted) "✓" else "$current/$target",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCompleted) 
                        Success 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = if (isCompleted) Success else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun YearInReviewCard(
    entries: List<com.onelineaday.dailydiary.data.JournalEntry>,
    modifier: Modifier = Modifier
) {
    val currentYear = java.time.LocalDate.now().year
    val thisYearEntries = entries.filter { it.date.year == currentYear }
    
    val totalEntries = thisYearEntries.size
    val topMood = thisYearEntries.groupBy { it.mood }
        .maxByOrNull { it.value.size }?.key
        
    // Calculate longest streak for this year
    var longestStreak = 0
    var currentStreak = 0
    var previousDate: java.time.LocalDate? = null
    
    thisYearEntries.sortedBy { it.date }.forEach { entry ->
        if (previousDate == null) {
            currentStreak = 1
        } else {
            val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(previousDate, entry.date)
            if (daysBetween == 1L) {
                currentStreak++
            } else if (daysBetween > 1L) {
                currentStreak = 1
            }
        }
        if (currentStreak > longestStreak) {
            longestStreak = currentStreak
        }
        previousDate = entry.date
    }

    androidx.compose.material3.Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎊",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$currentYear in Review",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                YearStatItem(
                    label = "Entries",
                    value = totalEntries.toString(),
                    modifier = Modifier.weight(1f)
                )
                YearStatItem(
                    label = "Top Mood",
                    value = topMood?.emoji ?: "-",
                    modifier = Modifier.weight(1f)
                )
                YearStatItem(
                    label = "Best Streak",
                    value = "$longestStreak days",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun YearStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
