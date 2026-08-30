package com.onelineaday.dailydiary.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import com.onelineaday.dailydiary.ui.components.*
import com.onelineaday.dailydiary.ui.components.PhotoAttachment
import com.onelineaday.dailydiary.ui.components.PremiumDialog
import com.onelineaday.dailydiary.ui.components.AudioAttachment
import com.onelineaday.dailydiary.ui.theme.*
import com.onelineaday.dailydiary.viewmodel.JournalViewModel
import com.onelineaday.dailydiary.ads.InterstitialAdManager
import com.onelineaday.dailydiary.ads.BannerAdView
import android.app.Activity
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: JournalViewModel,
    uiState: com.onelineaday.dailydiary.viewmodel.JournalUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    // Form always starts empty — never pre-populated from todayEntry
    var entryText by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf(Mood.NEUTRAL) }
    var photoUri by remember { mutableStateOf<String?>(null) }
    
    val audioFile = remember(uiState.selectedDate) { java.io.File(context.filesDir, "audio_${uiState.selectedDate}.m4a") }
    var hasAudio by remember(uiState.selectedDate) { androidx.compose.runtime.mutableStateOf(audioFile.exists()) }
    
    // Controls whether to show the edit form or the "captured" card
    var isEditing by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    
    var showPremiumDialog by remember { mutableStateOf(false) }
    
    // When entry is saved, clear the form and exit editing mode
    LaunchedEffect(uiState.entrySaved) {
        if (uiState.entrySaved) {
            entryText = ""
            selectedMood = Mood.NEUTRAL
            photoUri = null
            hasAudio = false
            isEditing = false
            viewModel.clearEntrySavedFlag()
        }
    }
    
    val today = LocalDate.now()
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
    
    val throwback = remember(uiState.entries, today) {
        val throwbackEntry = uiState.entries.firstOrNull { 
            it.date.monthValue == today.monthValue && 
            it.date.dayOfMonth == today.dayOfMonth && 
            it.date.year < today.year 
        }
        if (throwbackEntry != null) {
            val yearsAgo = today.year - throwbackEntry.date.year
            Pair(yearsAgo, throwbackEntry)
        } else {
            null
        }
    }
    
    // Determine whether to show the write form or the "captured" card
    val showWriteForm = uiState.todayEntry == null || isEditing
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {}
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.today),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = today.format(dateFormatter),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                val isPremium by com.onelineaday.dailydiary.PremiumManager.isPremium.collectAsState()
                if (!isPremium) {
                    IconButton(
                        onClick = { showPremiumDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = "Go Premium",
                            tint = Color(0xFFFFA500)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Throwback Card
            if (throwback != null) {
                val (yearsAgo, entry) = throwback
                Text(
                    text = "🕰️ $yearsAgo Year${if (yearsAgo > 1) "s" else ""} Ago Today...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                EntryCard(
                    entry = entry,
                    onClick = { /* Could open detail view, but for now just display */ }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Streak mini card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SunsetOrange.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🔥", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${uiState.currentStreak}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SunsetOrange
                            )
                            Text(
                                text = stringResource(R.string.home_day_streak),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // Total entries mini card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = AccentTeal.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "📝", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${uiState.totalEntries}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = AccentTeal
                            )
                            Text(
                                text = stringResource(R.string.home_memories),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (showWriteForm) {
                // ── Write / Edit Form ──
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(28.dp),
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        // Mood Picker
                        MoodPicker(
                            selectedMood = selectedMood,
                            onMoodSelected = { 
                                selectedMood = it
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) 
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Stylish Text Input with Gradient Colors
                        StylishTextInput(
                            value = entryText,
                            onValueChange = { entryText = it },
                            placeholder = stringResource(R.string.write_your_line)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${entryText.trim().split("\\s+".toRegex()).count { it.isNotEmpty() }} words • ${entryText.length} characters",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.End)
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Photo Attachment
                        PhotoAttachment(
                            photoUri = photoUri,
                            onPhotoSelected = { uri ->
                                val savedPath = viewModel.saveMediaToInternal(context, uri)
                                photoUri = savedPath
                            },
                            onPhotoRemoved = { photoUri = null }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Audio Attachment
                        AudioAttachment(
                            dateKey = uiState.selectedDate.toString(),
                            onAudioSaved = { hasAudio = true },
                            onAudioDeleted = { hasAudio = false }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Save Button
                        val canSave = entryText.isNotBlank() || photoUri != null || hasAudio
                        Button(
                            onClick = {
                                if (canSave) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.saveEntry(entryText, selectedMood, photoUri)
                                    val activity = context.findActivity()
                                    if (activity != null) {
                                        InterstitialAdManager.onEntrySaved(activity)
                                    }
                                }
                            },
                            enabled = canSave,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (uiState.todayEntry != null) Icons.Rounded.Edit else Icons.Rounded.Check,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.todayEntry != null) stringResource(R.string.home_update_entry) else stringResource(R.string.home_save_entry),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            } else {
                // ── Today's Memory Captured Card ──
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(28.dp),
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "✅",
                            style = MaterialTheme.typography.displaySmall
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = stringResource(R.string.home_today_captured),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Show saved mood and content
                        uiState.todayEntry?.let { entry ->
                            Text(
                                text = "${entry.mood.emoji} ${entry.mood.label}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Show saved content preview
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = entry.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Edit button to go back to form mode
                        OutlinedButton(
                            onClick = {
                                // Pre-fill the form with existing entry for editing
                                uiState.todayEntry?.let { entry ->
                                    entryText = entry.content
                                    selectedMood = entry.mood
                                    photoUri = entry.photoUri
                                }
                                isEditing = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.home_update_entry),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Motivational Message (only when no entry at all)
            if (uiState.totalEntries == 0) {
                EmptyStateMessage()
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            BannerAdView()
            Spacer(modifier = Modifier.height(84.dp))
        }
    }
    
    // Show snackbar for messages
    uiState.message?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearMessage()
        }
    }
    
    if (showPremiumDialog) {
        PremiumDialog(
            onDismiss = { showPremiumDialog = false }
        )
    }
}

@Composable
fun EmptyStateMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🌟",
            style = MaterialTheme.typography.displayLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.home_start_journey),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.home_start_writing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
