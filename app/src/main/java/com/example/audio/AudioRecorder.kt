package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFilePath: String? = null

    fun startRecording(outputFile: File) {
        currentFilePath = outputFile.absolutePath
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentFilePath)
            prepare()
            start()
        }
    }

    fun stopRecording(): String? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            currentFilePath
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            null
        }
    }
}
