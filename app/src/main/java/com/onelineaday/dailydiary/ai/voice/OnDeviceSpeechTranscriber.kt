package com.onelineaday.dailydiary.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class SpeechError {
    OFFLINE_PACK_UNAVAILABLE,
    PERMISSION_DENIED,
    NO_SPEECH_DETECTED,
    CLIENT_ERROR,
    UNSUPPORTED
}

sealed class TranscriptionState {
    object Idle : TranscriptionState()
    object Listening : TranscriptionState()
    data class Partial(val text: String) : TranscriptionState()
    data class Success(val text: String) : TranscriptionState()
    data class Error(val error: SpeechError, val message: String) : TranscriptionState()
}

/**
 * Privacy-first On-Device Voice Transcriber.
 * Uses Android platform SpeechRecognizer strictly with EXTRA_PREFER_OFFLINE = true,
 * ensuring no voice audio is streamed to the cloud without explicit authorization.
 */
class OnDeviceSpeechTranscriber(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    fun isOfflineRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun startListening(
        locale: Locale = Locale.getDefault(),
        onPartial: (String) -> Unit = {},
        onFinal: (String) -> Unit = {},
        onError: (SpeechError, String) -> Unit = { _, _ -> }
    ) {
        if (!isOfflineRecognitionAvailable()) {
            val err = SpeechError.UNSUPPORTED
            val msg = "Speech recognition is not available on this device."
            _state.value = TranscriptionState.Error(err, msg)
            onError(err, msg)
            return
        }

        stopListening()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _state.value = TranscriptionState.Listening
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(errorCode: Int) {
                        val (errType, message) = when (errorCode) {
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                                SpeechError.OFFLINE_PACK_UNAVAILABLE to "Offline speech model not installed for this language."
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                SpeechError.PERMISSION_DENIED to "Microphone permission is required."
                            }
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                SpeechError.NO_SPEECH_DETECTED to "No speech detected. Please try again."
                            }
                            else -> {
                                SpeechError.CLIENT_ERROR to "Recognition error ($errorCode)."
                            }
                        }
                        _state.value = TranscriptionState.Error(errType, message)
                        onError(errType, message)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        _state.value = TranscriptionState.Success(text)
                        onFinal(text)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _state.value = TranscriptionState.Partial(text)
                            onPartial(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Enforce strictly offline processing
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }

            speechRecognizer?.startListening(intent)
            _state.value = TranscriptionState.Listening
        } catch (t: Throwable) {
            val msg = t.message ?: "Failed to initialize speech recognizer."
            _state.value = TranscriptionState.Error(SpeechError.CLIENT_ERROR, msg)
            onError(SpeechError.CLIENT_ERROR, msg)
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Throwable) {
        } finally {
            speechRecognizer = null
            _state.value = TranscriptionState.Idle
        }
    }
}

