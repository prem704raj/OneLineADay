package com.onelineaday.dailydiary.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.memory.FlashbackMemory
import com.onelineaday.dailydiary.memory.ui.OnThisDayCard
import com.onelineaday.dailydiary.utils.ShareImageHelper
import com.onelineaday.dailydiary.utils.StoryFormat
import com.onelineaday.dailydiary.viewmodel.JournalViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnThisDayScreen(
    viewModel: JournalViewModel,
    initialDate: LocalDate = LocalDate.now(),
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (JournalEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf(initialDate) }
    var selectedYearOffsetFilter by remember { mutableStateOf<Int?>(null) } // null = All
    var showDatePicker by remember { mutableStateOf(false) }

    // Collect flashbacks reactively for selectedDate
    val flashbacksFlow = remember(selectedDate, viewModel) {
        viewModel.getFlashbacksFlow(selectedDate)
    }
    val allFlashbacks by flashbacksFlow.collectAsState(initial = emptyList())

    val filteredFlashbacks = remember(allFlashbacks, selectedYearOffsetFilter) {
        if (selectedYearOffsetFilter == null) {
            allFlashbacks
        } else {
            allFlashbacks.filter { it.yearOffset == selectedYearOffsetFilter }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            selectedYearOffsetFilter = null
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.on_this_day_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedDate.format(DateTimeFormatter.ofPattern("MMMM d")),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (allFlashbacks.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    ShareImageHelper.shareMultiYearFlashbackStoryAsImage(
                                        context = context,
                                        flashbacks = allFlashbacks,
                                        currentDate = selectedDate,
                                        format = StoryFormat.VERTICAL_9_16
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share Timeline Story"
                            )
                        }
                    }
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = stringResource(R.string.calendar)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Filter Bar (if there are memories across different years)
            if (allFlashbacks.isNotEmpty()) {
                val availableOffsets = remember(allFlashbacks) {
                    allFlashbacks.map { it.yearOffset to it.entry.date.year }.distinct().sortedBy { it.first }
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedYearOffsetFilter == null,
                            onClick = { selectedYearOffsetFilter = null },
                            label = { Text("All (${allFlashbacks.size})") }
                        )
                    }

                    items(availableOffsets) { (offset, year) ->
                        FilterChip(
                            selected = selectedYearOffsetFilter == offset,
                            onClick = {
                                selectedYearOffsetFilter = if (selectedYearOffsetFilter == offset) null else offset
                            },
                            label = { Text("$year (${FlashbackMemory.formatTimeAgo(offset)})") }
                        )
                    }
                }
            }

            // Main Content Area
            if (filteredFlashbacks.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "No Memories on This Day Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.on_this_day_no_memories),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onNavigateBack,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(R.string.write_now))
                        }
                    }
                }
            } else {
                // List of flashback cards
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        items = filteredFlashbacks,
                        key = { "${it.entry.journalId}_${it.entry.date}" }
                    ) { memory ->
                        OnThisDayCard(
                            memory = memory,
                            onCardClick = { onNavigateToDetail(memory.entry) },
                            onShareClick = {
                                scope.launch {
                                    ShareImageHelper.shareStoryCardAsImage(
                                        context = context,
                                        entry = memory.entry,
                                        format = StoryFormat.VERTICAL_9_16,
                                        yearOffset = memory.yearOffset,
                                        isLeapDayObservation = memory.isLeapDayObservation
                                    )
                                }
                            },
                            onEditClick = { onNavigateToDetail(memory.entry) }
                        )
                    }
                }
            }
        }
    }
}
