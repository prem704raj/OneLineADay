package com.onelineaday.dailydiary.ui.screens

import androidx.compose.ui.res.stringResource
import com.onelineaday.dailydiary.R
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.data.Mood
import com.onelineaday.dailydiary.ui.components.AudioPlayerView
import com.onelineaday.dailydiary.ui.components.MoodBadge
import com.onelineaday.dailydiary.ui.components.StylishEntryText
import com.onelineaday.dailydiary.ui.components.MediaGallery
import com.onelineaday.dailydiary.utils.ShareTheme
import com.onelineaday.dailydiary.utils.ShareImageHelper

import com.onelineaday.dailydiary.ui.components.getMoodColor
import com.onelineaday.dailydiary.ui.theme.*
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entry: JournalEntry,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(ShareTheme.forMood(entry.mood)) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isGeneratingPreview by remember { mutableStateOf(false) }

    LaunchedEffect(showShareDialog, selectedTheme) {
        if (showShareDialog) {
            isGeneratingPreview = true
            previewBitmap = withContext(Dispatchers.IO) {
                ShareImageHelper.generateEntryBitmap(context, entry, selectedTheme)
            }
            isGeneratingPreview = false
        }
    }

    androidx.activity.compose.BackHandler(onBack = onBack)
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.entry_details),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    if (onTogglePin != null) {
                        IconButton(onClick = { onTogglePin.invoke() }) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = if (entry.isPinned) "Unpin" else "Pin",
                                tint = if (entry.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { showShareDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { onEdit() }) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
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
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // Date and Time Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = entry.date.format(DateTimeFormatter.ofPattern("EEEE")),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = entry.date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        MoodBadge(mood = entry.mood)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Time info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TimeInfoItem(
                            icon = Icons.Rounded.Create,
                            label = "Created",
                            time = SimpleDateFormat("h:mm a", Locale.getDefault())
                                .format(Date(entry.createdAt))
                        )
                        
                        TimeInfoItem(
                            icon = Icons.Rounded.Edit,
                            label = "Updated",
                            time = SimpleDateFormat("h:mm a", Locale.getDefault())
                                .format(Date(entry.updatedAt))
                        )
                    }
                }
            }
            
            // Audio Card
            Spacer(modifier = Modifier.height(20.dp))
            AudioPlayerView(audioUri = entry.audioUri, dateKey = entry.date.toString())
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Content Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.your_line),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Stylish gradient text display
                    StylishEntryText(
                        text = entry.content
                    )
                    
                    if (entry.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            entry.tags.forEach { tag ->
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(
                                        text = "#$tag",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Media Gallery
            val allMedia = entry.mediaUris + listOfNotNull(entry.photoUri).filter { it !in entry.mediaUris }
            if (allMedia.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.media_attached),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        MediaGallery(
                            mediaUris = allMedia,
                            onMediaSelected = {},
                            onMediaRemoved = {},
                            isEditable = false
                        )
                    }
                }
            }
            
            // Mood details
            Spacer(modifier = Modifier.height(20.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = getMoodColor(entry.mood).copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = entry.mood.emoji,
                        style = MaterialTheme.typography.displaySmall
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = stringResource(R.string.you_were_feeling),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = entry.mood.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = getMoodColor(entry.mood)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { 
                Text(
                    stringResource(R.string.delete_entry_title),
                    style = MaterialTheme.typography.titleLarge
                ) 
            },
            text = { 
                Text(
                    stringResource(R.string.delete_entry_message),
                    style = MaterialTheme.typography.bodyMedium
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Share Entry Card",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showShareDialog = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Ultra HD Card Preview
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isGeneratingPreview || previewBitmap == null) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        } else {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "Card Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Choose Colourful Page Style",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Colorful Theme Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ShareTheme.values()) { theme ->
                            FilterChip(
                                selected = selectedTheme == theme,
                                onClick = { selectedTheme = theme },
                                label = { Text("${theme.emoji} ${theme.title}", style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(androidx.compose.ui.graphics.Color(theme.bgEndColor))
                                    )
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        previewBitmap?.let { bmp ->
                            ShareImageHelper.shareBitmap(context, bmp)
                        }
                        showShareDialog = false
                    },
                    enabled = previewBitmap != null && !isGeneratingPreview,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share in Ultra HD")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showShareDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
}

@Composable
fun TimeInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    time: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

