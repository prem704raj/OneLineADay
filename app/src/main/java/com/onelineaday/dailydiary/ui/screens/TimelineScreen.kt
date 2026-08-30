package com.onelineaday.dailydiary.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import com.onelineaday.dailydiary.ui.components.MediaGallery
import com.onelineaday.dailydiary.ui.components.*
import com.onelineaday.dailydiary.ui.theme.*
import com.onelineaday.dailydiary.ads.BannerAdView
import com.onelineaday.dailydiary.ads.InterstitialAdManager
import com.onelineaday.dailydiary.ads.NativeAdCard
import android.app.Activity
import com.onelineaday.dailydiary.viewmodel.JournalUiState
import com.onelineaday.dailydiary.viewmodel.JournalViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: JournalViewModel,
    uiState: JournalUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var selectedMoodFilter by remember { mutableStateOf<Mood?>(null) }
    var showOnlyPinned by remember { mutableStateOf(false) }
    var selectedTagFilter by remember { mutableStateOf<String?>(null) }
    var selectedEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var editingEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    val baseEntries = if (searchQuery.isNotEmpty()) {
        uiState.searchResults
    } else {
        uiState.entries
    }
    
    var entries = if (selectedMoodFilter != null) {
        baseEntries.filter { it.mood == selectedMoodFilter }
    } else {
        baseEntries
    }
    
    if (showOnlyPinned) {
        entries = entries.filter { it.isPinned }
    }
    
    if (selectedTagFilter != null) {
        entries = entries.filter { it.tags.contains(selectedTagFilter) }
    }
    
    // Show edit dialog if editing
    editingEntry?.let { entry ->
        EditEntryDialog(
            entry = entry,
            viewModel = viewModel,
            onDismiss = { editingEntry = null },
            onSave = { newContent, newMood, newPhotoUri, newMediaUris ->
                viewModel.updateEntry(entry, newContent, newMood, newPhotoUri, newMediaUris)
                editingEntry = null
                
                // Show Interstitial Ad (every N edits)
                (context as? Activity)?.let { activity ->
                    InterstitialAdManager.onEntrySaved(activity)
                }
            }
        )
    }
    
    // Show detail screen if entry is selected
    selectedEntry?.let { entry ->
        EntryDetailScreen(
            entry = entry,
            onBack = { selectedEntry = null },
            onEdit = { editingEntry = entry },
            onDelete = {
                viewModel.deleteEntry(entry)
                selectedEntry = null
            },
            onTogglePin = {
                val updatedEntry = entry.copy(isPinned = !entry.isPinned)
                viewModel.togglePin(entry)
                selectedEntry = updatedEntry
            }
        )
        return
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (!showSearch) {
                            Text(
                                text = stringResource(R.string.timeline),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        AnimatedVisibility(
                            visible = showSearch,
                            enter = expandHorizontally() + fadeIn(),
                            exit = shrinkHorizontally() + fadeOut()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { 
                                    searchQuery = it
                                    viewModel.search(it)
                                },
                                placeholder = { Text(stringResource(R.string.timeline_search)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                        if (!showSearch) {
                            IconButton(
                                onClick = { showSearch = true },
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.timeline_open_search),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            
                            IconButton(
                                onClick = { showFilter = !showFilter },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FilterList,
                                    contentDescription = "Filter",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            
                            IconButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarMonth,
                                    contentDescription = "Calendar",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    showSearch = false
                                    searchQuery = ""
                                    viewModel.search("")
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.timeline_close_search)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                
                // Filter Row
                AnimatedVisibility(
                    visible = showFilter,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val allTags = remember(baseEntries) { baseEntries.flatMap { it.tags }.distinct().sorted() }
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedMoodFilter == null && !showOnlyPinned && selectedTagFilter == null,
                                onClick = { 
                                    selectedMoodFilter = null
                                    showOnlyPinned = false
                                    selectedTagFilter = null
                                },
                                label = { Text(stringResource(R.string.filter_all)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = showOnlyPinned,
                                onClick = { showOnlyPinned = !showOnlyPinned },
                                label = { Text("📌 Pinned") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                        items(Mood.values()) { mood ->
                            FilterChip(
                                selected = selectedMoodFilter == mood,
                                onClick = { selectedMoodFilter = mood },
                                label = { Text("${mood.emoji} ${mood.label}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                        
                        items(allTags) { tag ->
                            FilterChip(
                                selected = selectedTagFilter == tag,
                                onClick = { selectedTagFilter = if (selectedTagFilter == tag) null else tag },
                                label = { Text("#$tag") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (entries.isEmpty()) {
                    // Empty State
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "🔍" else "📚",
                                style = MaterialTheme.typography.displayLarge
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Text(
                                text = if (searchQuery.isNotEmpty()) 
                                    "No entries found" 
                                else 
                                    stringResource(R.string.timeline_your_timeline_awaits),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = if (searchQuery.isNotEmpty())
                                    stringResource(R.string.timeline_try_different)
                                else
                                    stringResource(R.string.timeline_start_writing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        itemsIndexed(
                            items = entries,
                            key = { _, entry -> entry.date.toString() }
                        ) { index, entry ->
                            val dateLabel = entry.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                            
                            EntryCard(
                                entry = entry,
                                dateLabel = dateLabel,
                                onClick = { selectedEntry = entry },
                                modifier = Modifier.animateItemPlacement()
                            )
                            
                            // Show native ad every 2 items
                            if ((index + 1) % 2 == 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                // Wrap Native Ad in timeline layout
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .padding(horizontal = 8.dp)
                                        .animateItemPlacement()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(56.dp)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.TopCenter
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.dp)
                                                .fillMaxHeight()
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(vertical = 12.dp)
                                    ) {
                                        NativeAdCard()
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
            
            BannerAdView()
        }
    }
    
    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                            
                            val index = entries.indexOfFirst { it.date == selectedDate || it.date.isBefore(selectedDate) }
                            if (index != -1) {
                                scope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun MonthHeader(
    monthYear: String,
    entryCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(GradientStart, GradientEnd)
                        )
                    )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = monthYear,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = if (entryCount == 1) stringResource(R.string.timeline_entry_count, entryCount) else stringResource(R.string.timeline_entries_count, entryCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryDialog(
    entry: JournalEntry,
    viewModel: JournalViewModel,
    onDismiss: () -> Unit,
    onSave: (String, Mood, String?, List<String>) -> Unit
) {
    val context = LocalContext.current
    var editedText by remember { mutableStateOf(entry.content) }
    var editedMood by remember { mutableStateOf(entry.mood) }
    var editedMediaUris by remember { mutableStateOf(entry.mediaUris + listOfNotNull(entry.photoUri).filter { it !in entry.mediaUris }) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.timeline_edit_entry),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Date display
                Text(
                    text = entry.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Mood picker
                Text(
                    text = stringResource(R.string.timeline_mood),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Mood.entries.forEach { mood ->
                        Surface(
                            onClick = { editedMood = mood },
                            shape = RoundedCornerShape(12.dp),
                            color = if (editedMood == mood) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = mood.emoji,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
                
                // Text input
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    label = { Text(stringResource(R.string.timeline_your_line)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    maxLines = 4
                )
                
                // Media section
                MediaGallery(
                    mediaUris = editedMediaUris,
                    onMediaSelected = { uris ->
                        val newPaths = uris.mapNotNull { viewModel.saveMediaToInternal(context, it) }
                        editedMediaUris = editedMediaUris + newPaths
                    },
                    onMediaRemoved = { uri ->
                        editedMediaUris = editedMediaUris.filter { it != uri }
                    },
                    isEditable = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (editedText.isNotBlank()) {
                        onSave(editedText, editedMood, editedMediaUris.firstOrNull(), editedMediaUris) 
                    }
                },
                enabled = editedText.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
