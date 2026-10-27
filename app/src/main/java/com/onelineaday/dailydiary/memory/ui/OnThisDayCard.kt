package com.onelineaday.dailydiary.memory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.memory.FlashbackMemory
import com.onelineaday.dailydiary.ui.components.AudioPlayerView
import com.onelineaday.dailydiary.ui.components.getMoodColor
import java.io.File
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnThisDayCard(
    memory: FlashbackMemory,
    onCardClick: () -> Unit,
    onShareClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val entry = memory.entry
    val moodColor = getMoodColor(entry.mood)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = moodColor.copy(alpha = 0.15f),
                spotColor = moodColor.copy(alpha = 0.2f)
            )
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Header Row: Badges & Mood
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    YearBadge(
                        yearOffset = memory.yearOffset,
                        year = entry.date.year
                    )
                    if (memory.isLeapDayObservation) {
                        LeapDayBadge(
                            isObserved = true,
                            originalYear = entry.date.year
                        )
                    }
                }

                // Mood Indicator
                Surface(
                    shape = CircleShape,
                    color = moodColor.copy(alpha = 0.15f),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = "${entry.mood.emoji} ${entry.mood.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = moodColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Date Subtitle
            Text(
                text = entry.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Main Text Content
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )

            // Media Preview (if any)
            if (memory.hasMedia) {
                Spacer(modifier = Modifier.height(12.dp))
                val mediaUri = memory.allMediaUris.first()
                val isVideo = mediaUri.endsWith(".mp4") || mediaUri.contains("video")
                val mediaData = remember(mediaUri) {
                    if (mediaUri.startsWith("/")) {
                        com.onelineaday.dailydiary.media.MediaStorageManager.resolveMediaFile(context, mediaUri) ?: File(mediaUri)
                    } else {
                        android.net.Uri.parse(mediaUri)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(mediaData)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Flashback photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isVideo) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircleOutline,
                                contentDescription = "Play Video",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    if (memory.allMediaUris.size > 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.65f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "+${memory.allMediaUris.size - 1}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Voice Note Audio Player
            if (memory.hasAudio) {
                Spacer(modifier = Modifier.height(12.dp))
                AudioPlayerView(
                    audioUri = entry.audioUri,
                    dateKey = "flashback_${entry.date}"
                )
            }

            // Tags
            if (entry.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    entry.tags.forEach { tag ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onShareClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.share_story_card),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.share_story_card),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                TextButton(
                    onClick = onEditClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.edit),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.edit),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
