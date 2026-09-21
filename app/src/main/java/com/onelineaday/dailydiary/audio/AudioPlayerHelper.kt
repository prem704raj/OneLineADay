package com.onelineaday.dailydiary.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayerHelper(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    fun play(audioFile: File) {
        if (!audioFile.exists()) return
        
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer.create(context, Uri.fromFile(audioFile))
                mediaPlayer?.setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPosition.value = 0
                    progressJob?.cancel()
                }
            }
            mediaPlayer?.let { player ->
                player.start()
                _duration.value = player.duration.coerceAtLeast(0)
                _isPlaying.value = true
                startProgressTracker()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
            progressJob?.cancel()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                val pos = runCatching { mediaPlayer?.currentPosition }.getOrNull() ?: 0
                _currentPosition.value = pos
                delay(100)
            }
        }
    }
    
    fun pause() {
        progressJob?.cancel()
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                _currentPosition.value = it.currentPosition
            }
        }
    }
    
    fun stop() {
        progressJob?.cancel()
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentPosition.value = 0
        _duration.value = 0
    }
    
    fun release() {
        stop()
        scope.cancel()
    }
}
