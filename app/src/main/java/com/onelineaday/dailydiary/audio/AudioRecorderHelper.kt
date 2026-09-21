package com.onelineaday.dailydiary.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderHelper(
    private val context: Context
) {

    private var mediaRecorder: MediaRecorder? = null

    var isRecording: Boolean = false
        private set

    fun startRecording(outputFile: File): Result<Unit> {
        if (isRecording || mediaRecorder != null) {
            return Result.failure(
                IllegalStateException("A recording is already active.")
            )
        }

        outputFile.parentFile?.mkdirs()

        val recorder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

        return try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(outputFile.absolutePath)

            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            isRecording = true

            Result.success(Unit)
        } catch (t: Throwable) {
            safelyRelease(recorder)

            mediaRecorder = null
            isRecording = false

            runCatching {
                if (outputFile.exists()) {
                    outputFile.delete()
                }
            }

            Result.failure(t)
        }
    }

    fun stopRecording(outputFile: File): Result<File> {
        val recorder =
            mediaRecorder
                ?: return Result.failure(
                    IllegalStateException("No recording is active.")
                )

        if (!isRecording) {
            safelyRelease(recorder)
            mediaRecorder = null

            return Result.failure(
                IllegalStateException("Recorder state is invalid.")
            )
        }

        return try {
            recorder.stop()

            if (!outputFile.exists() || outputFile.length() <= 0L) {
                throw IllegalStateException("The recording file is empty.")
            }

            Result.success(outputFile)
        } catch (t: Throwable) {
            runCatching {
                if (outputFile.exists()) {
                    outputFile.delete()
                }
            }

            Result.failure(t)
        } finally {
            safelyRelease(recorder)
            mediaRecorder = null
            isRecording = false
        }
    }

    fun cancelRecording(outputFile: File?) {
        val recorder = mediaRecorder

        if (recorder != null) {
            runCatching {
                if (isRecording) {
                    recorder.stop()
                }
            }

            safelyRelease(recorder)
        }

        mediaRecorder = null
        isRecording = false

        outputFile?.let { file ->
            runCatching {
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    private fun safelyRelease(recorder: MediaRecorder) {
        runCatching { recorder.reset() }
        runCatching { recorder.release() }
    }
}
