package com.onelineaday.dailydiary.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import com.onelineaday.dailydiary.mosaic.MoodMosaicEngine
import com.onelineaday.dailydiary.mosaic.PixelCell
import com.onelineaday.dailydiary.mosaic.YearInPixelsShareHelper
import com.onelineaday.dailydiary.ui.components.getMoodColor
import com.onelineaday.dailydiary.viewmodel.JournalUiState
import com.onelineaday.dailydiary.viewmodel.JournalViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearInPixelsScreen(
    viewModel: JournalViewModel,
    uiState: JournalUiState,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (JournalEntry) -> Unit,
    onNavigateToWrite: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedYear by remember { mutableStateOf(LocalDate.now().year) }

    // Filter entries for the selected year
    val yearEntries = remember(uiState.entries, selectedYear) {
        uiState.entries.filter { it.date.year == selectedYear }
    }

    val moodMap = remember(yearEntries) {
        yearEntries.associate { it.date to it.mood }
    }

    val entriesByDate = remember(yearEntries) {
        yearEntries.associateBy { it.date }
    }

    val grid = remember(selectedYear, moodMap) {
        MoodMosaicEngine.buildMosaic(selectedYear, moodMap)
    }

    val stats = remember(moodMap) {
        MoodMosaicEngine.calculateStats(moodMap)
    }

    val verticalScrollState = rememberScrollState()
    val horizontalGridScrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Year in Pixels",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            YearInPixelsShareHelper.shareMosaicCard(context, selectedYear, moodMap)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share Year in Pixels",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
                .verticalScroll(verticalScrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Year Selector Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedYear-- }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Year")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$selectedYear",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (selectedYear != LocalDate.now().year) {
                            TextButton(
                                onClick = { selectedYear = LocalDate.now().year },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("This Year", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    IconButton(onClick = { selectedYear++ }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Year")
                    }
                }
            }

            // 2. Factual Annual Stats Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Total Days Logged
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${stats.totalLogged}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Days Logged",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(36.dp))

                    // Dominant Mood
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val dominant = stats.dominantMood
                        Text(
                            text = dominant?.emoji ?: "—",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = dominant?.label ?: "No Mood",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    VerticalDivider(modifier = Modifier.height(36.dp))

                    // Mood Stability
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val stabilityPct = (stats.stabilityScore * 100).toInt()
                        Text(
                            text = "$stabilityPct%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Stability Score",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Interactive 12x31 Mood Grid Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "12-Month Mood Mosaic",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap any day to view or record an entry",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scrollable Matrix Grid
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Fixed Month Column
                        Column(
                            modifier = Modifier.width(42.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Header placeholder
                            Box(modifier = Modifier.height(20.dp))
                            for (m in 1..12) {
                                val monthName = Month.of(m).getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3)
                                Box(
                                    modifier = Modifier
                                        .height(22.dp)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = monthName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Horizontally Scrollable 31 Day Columns
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalGridScrollState)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Day Numbers Header
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    for (day in 1..31) {
                                        Box(
                                            modifier = Modifier.size(22.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (day == 1 || day % 5 == 0 || day == 31) {
                                                Text(
                                                    text = "$day",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    fontSize = 9.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

                                // 12 Month Rows
                                for (m in 0 until 12) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        for (d in 0 until 31) {
                                            val cell = grid[m][d]
                                            when (cell) {
                                                is PixelCell.Logged -> {
                                                    val cellColor = getMoodColor(cell.mood)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(cellColor)
                                                            .clickable {
                                                                val entry = entriesByDate[cell.date]
                                                                if (entry != null) {
                                                                    onNavigateToDetail(entry)
                                                                }
                                                            }
                                                    )
                                                }
                                                is PixelCell.Unlogged -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(22.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                                            .border(
                                                                width = 0.5.dp,
                                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                                shape = RoundedCornerShape(4.dp)
                                                            )
                                                            .clickable {
                                                                onNavigateToWrite(cell.date)
                                                            }
                                                    )
                                                }
                                                is PixelCell.OutOfRange -> {
                                                    Box(modifier = Modifier.size(22.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Mood Palette Legend
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Mood Legend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // 2-column Flow / Grid of moods
                    val moods = Mood.values()
                    val chunked = moods.toList().chunked(2)

                    for (row in chunked) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (mood in row) {
                                val count = stats.moodCounts[mood] ?: 0
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(getMoodColor(mood))
                                    )
                                    Text(
                                        text = "${mood.emoji} ${mood.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "($count)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

