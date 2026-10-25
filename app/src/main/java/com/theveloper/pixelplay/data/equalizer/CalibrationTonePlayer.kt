package com.theveloper.pixelplay.data.equalizer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.pow

class CalibrationTonePlayer {
    companion object {
        const val START_LEVEL_DBFS = -36f
        const val MAX_LEVEL_DBFS = -6f
        private const val STEP_DB = 1f
        private const val STEP_DURATION_MS = 600
        private const val SAMPLE_RATE_HZ = 48_000
    }

    @Volatile private var activeTrack: AudioTrack? = null
    @Volatile private var stopRequested = false
    @Volatile var lastOutputRouteLabel: String = "Current output"
        private set

    suspend fun playRamp(
        frequencyHz: Int,
        audioSessionId: Int,
        onLevelChanged: (Float) -> Unit
    ): Float = withContext(Dispatchers.IO) {
        stopRequested = false
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE_HZ)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBufferBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(SAMPLE_RATE_HZ * Float.SIZE_BYTES / 10)
        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBufferBytes, SAMPLE_RATE_HZ * Float.SIZE_BYTES / 5))
            .setTransferMode(AudioTrack.MODE_STREAM)
        if (audioSessionId > 0) builder.setSessionId(audioSessionId)

        val track = builder.build()
        activeTrack = track
        try {
            track.play()
            lastOutputRouteLabel = track.routedDevices.firstOrNull()?.productName?.toString()
                ?.takeIf { it.isNotBlank() } ?: "Current output"
            var levelDbfs = START_LEVEL_DBFS
            var phase = 0.0
            var lastLevel = levelDbfs
            val frameCount = SAMPLE_RATE_HZ * STEP_DURATION_MS / 1000
            val fadeFrames = SAMPLE_RATE_HZ / 50
            val samples = FloatArray(frameCount)

            while (!stopRequested && levelDbfs <= MAX_LEVEL_DBFS) {
                lastLevel = levelDbfs
                onLevelChanged(levelDbfs)
                val amplitude = 10.0.pow(levelDbfs / 20.0).toFloat()
                val phaseStep = 2.0 * PI * frequencyHz / SAMPLE_RATE_HZ
                for (i in samples.indices) {
                    val fadeIn = (i.toFloat() / fadeFrames).coerceIn(0f, 1f)
                    val fadeOut = ((samples.size - 1 - i).toFloat() / fadeFrames).coerceIn(0f, 1f)
                    samples[i] = (amplitude * min(fadeIn, fadeOut) * sin(phase)).toFloat()
                    phase += phaseStep
                    if (phase >= 2.0 * PI) phase -= 2.0 * PI
                }
                var offset = 0
                while (offset < samples.size && !stopRequested) {
                    val written = track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written <= 0) break
                    offset += written
                }
                levelDbfs += STEP_DB
            }
            lastLevel
        } finally {
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            track.release()
            if (activeTrack === track) activeTrack = null
        }
    }

    fun stop() {
        stopRequested = true
        activeTrack?.let { track ->
            runCatching { track.pause() }
            runCatching { track.stop() }
        }
    }
}
