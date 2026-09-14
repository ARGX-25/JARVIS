package com.example.jarvis.ui.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.log10

private const val SampleRate = 16_000

/**
 * Live microphone level in 0..1 while [active]. The caller must already hold RECORD_AUDIO.
 * Only the level is read; no audio is kept or transcribed.
 */
@Composable
fun rememberMicLevel(active: Boolean): State<Float> {
    val level = remember { mutableFloatStateOf(0f) }
    val inPreview = LocalInspectionMode.current
    LaunchedEffect(active, inPreview) {
        level.floatValue = 0f
        if (!active || inPreview) return@LaunchedEffect
        withContext(Dispatchers.IO) { streamMicLevel { level.floatValue = it } }
    }
    return level
}

@SuppressLint("MissingPermission") // JarvisScreen checks RECORD_AUDIO before turning voice on
private suspend fun streamMicLevel(onLevel: (Float) -> Unit) {
    val minBuffer = AudioRecord.getMinBufferSize(SampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (minBuffer <= 0) return
    val recorder = AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        SampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        minBuffer * 2
    )
    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
        recorder.release()
        return
    }
    val buffer = ShortArray(SampleRate / 20) // 50 ms per reading
    try {
        recorder.startRecording()
        while (currentCoroutineContext().isActive) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read <= 0) continue
            var sumSquares = 0.0
            for (i in 0 until read) {
                val sample = buffer[i] / 32768.0
                sumSquares += sample * sample
            }
            val dbfs = 10 * log10(sumSquares / read + 1e-10)
            onLevel(((dbfs + 55) / 40).toFloat().coerceIn(0f, 1f)) // -55 dBFS -> 0, -15 dBFS -> 1
        }
    } finally {
        runCatching { recorder.stop() }
        recorder.release()
    }
}
