package com.onelineaday.dailydiary.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onelineaday.dailydiary.R
import com.onelineaday.dailydiary.ui.theme.*
import androidx.compose.foundation.ExperimentalFoundationApi
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGallery(
    mediaUris: List<String>,
    onMediaSelected: (List<Uri>) -> Unit,
    onMediaRemoved: (String) -> Unit,
    isEditable: Boolean,
    modifier: Modifier = Modifier,
    maxItems: Int = 10,
    isImporting: Boolean = false
) {
    val context = LocalContext.current
    
    var showViewer by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableIntStateOf(0) }
    
    val imageUris = remember(mediaUris) {
        mediaUris.filter { !isVideoMedia(it) }
    }
    
    // Create multiple visual media picker (maxItems limit)
    val multipleMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = maxItems)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val remaining = (maxItems - mediaUris.size).coerceAtLeast(0)
            val limited = uris.take(remaining)
            if (limited.isNotEmpty()) {
                onMediaSelected(limited)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (isEditable && mediaUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.media_attached),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${mediaUris.size} / $maxItems",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (mediaUris.size >= maxItems) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            }
        }

        if (mediaUris.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(mediaUris) { uriString ->
                    MediaItem(
                        uriString = uriString,
                        isEditable = isEditable,
                        onRemove = { onMediaRemoved(uriString) },
                        onImageClick = {
                            viewerIndex = imageUris.indexOf(uriString).coerceAtLeast(0)
                            showViewer = true
                        }
                    )
                }
                
                if (isEditable && mediaUris.size < maxItems) {
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

        if (isImporting) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
    
    if (showViewer && imageUris.isNotEmpty()) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showViewer = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            FullScreenImageViewer(
                imageUris = imageUris,
                initialIndex = viewerIndex,
                onDismiss = { showViewer = false }
            )
        }
    }
}

@Composable
private fun MediaItem(
    uriString: String,
    isEditable: Boolean,
    onRemove: () -> Unit,
    onImageClick: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = isVideoMedia(uriString)
    
    val resolvedFile = remember(uriString) {
        if (uriString.startsWith("/")) {
            com.onelineaday.dailydiary.media.MediaStorageManager.resolveMediaFile(context, uriString)
        } else null
    }
    
    val exists = resolvedFile?.exists() ?: true
    
    if (exists) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    if (isVideo) {
                        // Launch intent to view video
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            val uri = if (uriString.startsWith("/")) {
                                val targetFile = resolvedFile ?: File(uriString)
                                androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    targetFile
                                )
                            } else {
                                Uri.parse(uriString)
                            }
                            setDataAndType(uri, "video/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        onImageClick()
                    }
                }
        ) {
            val imageModel = remember(uriString, resolvedFile) {
                if (uriString.startsWith("/")) (resolvedFile ?: File(uriString)) else Uri.parse(uriString)
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true)
                    // If we use coil-video, it will generate a thumbnail for video files.
                    .build(),
                contentDescription = stringResource(R.string.media_attached_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
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
                        contentDescription = stringResource(R.string.play_video),
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
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.remove_media),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
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
                        Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
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
            text = stringResource(R.string.add_media_up_to_10),
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
                contentDescription = stringResource(R.string.add_media),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.add_media),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun isVideoMedia(pathOrUri: String): Boolean {
    val clean = pathOrUri.substringBefore('?').lowercase()
    val ext = clean.substringAfterLast('.', "")
    val videoExtensions = setOf("mp4", "webm", "3gp", "mkv", "mov", "ts", "avi")
    if (ext in videoExtensions) return true
    val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    if (mimeType?.startsWith("video/") == true) return true
    return clean.contains("video/") || clean.contains("/video") || clean.contains("video_")
}
