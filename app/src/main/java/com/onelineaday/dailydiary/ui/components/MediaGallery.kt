package com.onelineaday.dailydiary.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.onelineaday.dailydiary.ui.theme.*
import java.io.File

@Composable
fun MediaGallery(
    mediaUris: List<String>,
    onMediaSelected: (List<Uri>) -> Unit,
    onMediaRemoved: (String) -> Unit,
    isEditable: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Create multiple visual media picker
    val multipleMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onMediaSelected(uris)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (mediaUris.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(mediaUris) { uriString ->
                    MediaItem(
                        uriString = uriString,
                        isEditable = isEditable,
                        onRemove = { onMediaRemoved(uriString) }
                    )
                }
                
                if (isEditable) {
                    item {
                        AddMediaButtonSmall {
                            multipleMediaPicker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageAndVideo
                                )
                            )
                        }
                    }
                }
            }
        } else if (isEditable) {
            AddMediaButtonLarge {
                multipleMediaPicker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                    )
                )
            }
        }
    }
}

@Composable
private fun MediaItem(
    uriString: String,
    isEditable: Boolean,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = uriString.endsWith(".mp4") || uriString.contains("video")
    
    val mediaFile = remember(uriString) {
        if (uriString.startsWith("/")) File(uriString) else null
    }
    
    val exists = mediaFile?.exists() ?: true
    
    if (exists) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    // Launch intent to view media
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        val uri = if (uriString.startsWith("/")) {
                            androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                File(uriString)
                            )
                        } else {
                            Uri.parse(uriString)
                        }
                        setDataAndType(uri, if (isVideo) "video/*" else "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(mediaFile ?: uriString)
                    .crossfade(true)
                    // If we use coil-video, it will generate a thumbnail for video files.
                    .build(),
                contentDescription = "Attached media",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) {
                val state = painter.state
                when (state) {
                    is AsyncImagePainter.State.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is AsyncImagePainter.State.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BrokenImage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    else -> {
                        SubcomposeAsyncImageContent()
                    }
                }
            }
            
            // Video Play Icon overlay
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
            
            if (isEditable) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove media",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    } else {
        // Missing file UI
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.ImageNotSupported,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )
                if (isEditable) {
                    TextButton(onClick = onRemove) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMediaButtonLarge(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(
                colors = listOf(GradientStart, GradientEnd)
            )
        )
    ) {
        Icon(
            imageVector = Icons.Rounded.AddPhotoAlternate,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Add Media",
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun AddMediaButtonSmall(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(80.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp, 
                Brush.horizontalGradient(listOf(GradientStart, GradientEnd)),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Add Media",
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Add",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
