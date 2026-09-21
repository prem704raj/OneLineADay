package com.onelineaday.dailydiary.ui.components

import androidx.compose.ui.res.stringResource
import com.onelineaday.dailydiary.R
import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.onelineaday.dailydiary.audio.AudioPlayerHelper
import com.onelineaday.dailydiary.audio.AudioRecorderHelper
import com.onelineaday.dailydiary.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

fun createAudioDestination(context: Context): File {
    val directory = File(context.filesDir, "journal_media/audio").apply {
        mkdirs()
    }
    return File(directory, "${UUID.randomUUID()}.m4a")
}

fun resolveAudioFile(context: Context, audioUri: String?, dateKey: String?): File? {
    if (!audioUri.isNullOrBlank()) {
        val file = File(audioUri)
        if (file.exists()) return file
    }
    if (!dateKey.isNullOrBlank()) {
        val legacy = File(context.filesDir, "audio_${dateKey}.m4a")
        if (legacy.exists()) return legacy
    }
    return null
}

@Composable
fun AudioAttachment(
    audioUri: String? = null,
    dateKey: String? = null,
    modifier: Modifier = Modifier,
    onAudioSaved: (String) -> Unit = {},
    onAudioDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    
    val recorder = remember { AudioRecorderHelper(context) }
    val player = remember { AudioPlayerHelper(context) }
    
    val resolvedFile = remember(audioUri, dateKey) { resolveAudioFile(context, audioUri, dateKey) }
    val fileExists = resolvedFile != null && resolvedFile.exists()
    
    var pendingRecording by remember { mutableStateOf<File?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingTime by remember { mutableStateOf(0) }
    
    val isPlaying by player.isPlaying.collectAsState(initial = false)
    
    // Timer for recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingTime = 0
            while (true) {
                delay(1000)
                recordingTime++
            }
        }
    }
    
    // Cleanup player and recorder when leaving
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            if (recorder.isRecording || pendingRecording != null) {
                recorder.cancelRecording(pendingRecording)
            }
        }
    }

    fun beginRecording() {
        val destination = createAudioDestination(context)
        recorder.startRecording(destination)
            .onSuccess {
                pendingRecording = destination
                isRecording = true
            }
            .onFailure {
                pendingRecording = null
                isRecording = false
                Toast.makeText(
                    context,
                    "Failed to start recording",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            beginRecording()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = fileExists && !isRecording,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                resolvedFile?.let { file ->
                                    if (isPlaying) player.pause() else player.play(file)
                                }
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Voice Memory",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    var showDeleteConfirm by remember { mutableStateOf(false) }
                    
                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text(stringResource(R.string.delete_voice_memory_title)) },
                            text = { Text(stringResource(R.string.delete_voice_memory_message)) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showDeleteConfirm = false
                                        player.stop()
                                        onAudioDeleted()
                                    }
                                ) {
                                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            showDeleteConfirm = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete Audio",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        
        AnimatedVisibility(
            visible = isRecording,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SunsetOrange.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Recording",
                            tint = SunsetOrange
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = String.format("Recording... %02d:%02d", recordingTime / 60, recordingTime % 60),
                            style = MaterialTheme.typography.titleMedium,
                            color = SunsetOrange
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            val destination = pendingRecording ?: return@IconButton
                            recorder.stopRecording(destination)
                                .onSuccess { completedFile ->
                                    isRecording = false
                                    pendingRecording = null
                                    onAudioSaved(completedFile.absolutePath)
                                }
                                .onFailure {
                                    isRecording = false
                                    pendingRecording = null
                                    Toast.makeText(
                                        context,
                                        "Audio recording failed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        },
                        modifier = Modifier.background(SunsetOrange, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Stop Recording",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        
        AnimatedVisibility(
            visible = !fileExists && !isRecording,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            OutlinedButton(
                onClick = { 
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        beginRecording()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Record Voice Memory",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun AudioPlayerView(
    audioUri: String? = null,
    dateKey: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioFile = remember(audioUri, dateKey) { resolveAudioFile(context, audioUri, dateKey) }
    
    if (audioFile == null || !audioFile.exists()) return
    
    val player = remember { AudioPlayerHelper(context) }
    val isPlaying by player.isPlaying.collectAsState(initial = false)
    
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) player.pause() else player.play(audioFile)
                },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Voice Memory",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap to listen",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
