package com.onelineaday.dailydiary.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onelineaday.dailydiary.data.JournalEntry
import com.onelineaday.dailydiary.media.MediaStorageManager
import com.onelineaday.dailydiary.video.MonthlyReelEngine
import com.onelineaday.dailydiary.video.ReelItem
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReelScreen(
    entries: List<JournalEntry>,
    onBack: () -> Unit,
    onNavigateToDetail: (JournalEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var speedMultiplier by remember { mutableStateOf(1.0f) }
    var isPlaying by remember { mutableStateOf(true) }

    val reelItems = remember(entries, selectedMonth, speedMultiplier) {
        MonthlyReelEngine.buildMonthlyReel(entries, selectedMonth, 1.0f / speedMultiplier)
    }

    var currentIndex by remember(reelItems) { mutableStateOf(0) }

    // Auto-advance loop when playing
    LaunchedEffect(isPlaying, currentIndex, reelItems) {
        if (!isPlaying || reelItems.isEmpty()) return@LaunchedEffect
        val currentItem = reelItems.getOrNull(currentIndex) ?: return@LaunchedEffect
        delay(currentItem.durationMillis)
        if (currentIndex < reelItems.size - 1) {
            currentIndex++
        } else {
            currentIndex = 0 // loop
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF0F172A),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Monthly Photo Reel",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Month Selector Dropdown / Stepper
                    IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous Month", tint = Color.White)
                    }
                    Text(
                        text = selectedMonth.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(
                        onClick = { selectedMonth = selectedMonth.plusMonths(1) },
                        enabled = selectedMonth.isBefore(YearMonth.now())
                    ) {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = "Next Month",
                            tint = if (selectedMonth.isBefore(YearMonth.now())) Color.White else Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        if (reelItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MovieFilter,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = "No Photos for ${selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Attach photos to your daily lines to automatically watch your month play out like a mini-documentary.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val currentItem = reelItems.getOrElse(currentIndex) { reelItems[0] }
            val matchingEntry = entries.firstOrNull { it.date == currentItem.date }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Segmented Progress Bar across top
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    reelItems.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        index < currentIndex -> Color.White
                                        index == currentIndex -> Color(0xFF38BDF8)
                                        else -> Color.White.copy(alpha = 0.3f)
                                    }
                                )
                        )
                    }
                }

                // Cinematic Player View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black)
                ) {
                    val resolvedData = if (currentItem.photoUri.startsWith("/")) {
                        MediaStorageManager.resolveMediaFile(context, currentItem.photoUri) ?: File(currentItem.photoUri)
                    } else {
                        currentItem.photoUri
                    }

                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resolvedData)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Daily Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { isPlaying = !isPlaying }
                    )

                    // Top Date & Mood Badge
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = currentItem.mood.emoji, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = currentItem.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Bottom Overlay with Snippet
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(
                                text = currentItem.lineSnippet,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            if (matchingEntry != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = { onNavigateToDetail(matchingEntry) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF38BDF8))
                                ) {
                                    Text("View Complete Entry →")
                                }
                            }
                        }
                    }
                }

                // Playback Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speed Toggle
                    FilledTonalButton(
                        onClick = {
                            speedMultiplier = when (speedMultiplier) {
                                1.0f -> 1.5f
                                1.5f -> 2.0f
                                else -> 1.0f
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color.White
                        )
                    ) {
                        Text("${speedMultiplier}x Speed")
                    }

                    // Transport controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (currentIndex > 0) currentIndex--
                            },
                            enabled = currentIndex > 0
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                        }

                        FloatingActionButton(
                            onClick = { isPlaying = !isPlaying },
                            containerColor = Color(0xFF38BDF8),
                            contentColor = Color(0xFF0F172A),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play"
                            )
                        }

                        IconButton(
                            onClick = {
                                if (currentIndex < reelItems.size - 1) currentIndex++
                            },
                            enabled = currentIndex < reelItems.size - 1
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White)
                        }
                    }

                    // Frame Counter
                    Text(
                        text = "${currentIndex + 1} / ${reelItems.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

