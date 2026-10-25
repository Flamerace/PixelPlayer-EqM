package com.theveloper.pixelplay.data.equalizer

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubwooferCalibrationManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    @Volatile private var activeProfile: SubwooferCalibrationProfile? = null
    @Volatile private var cachedVolumeStep = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    @Volatile private var lastVolumeReadMs = 0L

    fun setActiveProfile(profile: SubwooferCalibrationProfile?) {
        activeProfile = profile
    }

    fun activeProfileForCurrentVolume(): SubwooferCalibrationProfile? {
        val now = SystemClock.elapsedRealtime()
        if (now - lastVolumeReadMs >= 250L) {
            cachedVolumeStep = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            lastVolumeReadMs = now
        }
        return activeProfile?.takeIf { it.volumeStep == cachedVolumeStep }
    }
}

@UnstableApi
class SubwooferCalibrationProcessor(
    private val calibrationManager: SubwooferCalibrationManager
) : BaseAudioProcessor() {
    private var sampleRateHz = 0
    private var channelCount = 0
    private var inputEncoding = C.ENCODING_PCM_16BIT
    private var configuredProfile: SubwooferCalibrationProfile? = null
    private var channelFilters: Array<Array<PeakingFilter>> = emptyArray()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if ((inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
                inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) ||
            inputAudioFormat.sampleRate <= 0 ||
            inputAudioFormat.channelCount <= 0
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        inputEncoding = inputAudioFormat.encoding
        configureFilters(calibrationManager.activeProfileForCurrentVolume())
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val input = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        val byteCount = input.remaining()
        if (byteCount == 0) return

        val profile = calibrationManager.activeProfileForCurrentVolume()
        if (profile != configuredProfile) configureFilters(profile)

        val output = replaceOutputBuffer(byteCount).order(ByteOrder.nativeOrder())
        when (inputEncoding) {
            C.ENCODING_PCM_FLOAT -> processFloat(input, output)
            C.ENCODING_PCM_16BIT -> processPcm16(input, output)
            else -> {
                output.put(input)
                output.flip()
            }
        }
        inputBuffer.position(inputBuffer.limit())
    }

    private fun processFloat(input: ByteBuffer, output: ByteBuffer) {
        val bytes = input.remaining()
        val sampleCount = bytes / Float.SIZE_BYTES
        val source = input.asFloatBuffer()
        val destination = output.asFloatBuffer()
        for (i in 0 until sampleCount) {
            val channel = i % channelCount
            val sample = source.get(i)
            val filtered = if (channelFilters.isEmpty()) sample else filterSample(channel, sample)
            destination.put(i, filtered.coerceIn(-1f, 1f))
        }
        output.position(bytes)
        output.flip()
    }

    private fun processPcm16(input: ByteBuffer, output: ByteBuffer) {
        val bytes = input.remaining()
        val sampleCount = bytes / Short.SIZE_BYTES
        val source = input.asShortBuffer()
        val destination = output.asShortBuffer()
        for (i in 0 until sampleCount) {
            val channel = i % channelCount
            val sample = source.get(i).toFloat() / 32768f
            val filtered = if (channelFilters.isEmpty()) sample else filterSample(channel, sample)
            val pcm = (filtered.coerceIn(-1f, 0.9999695f) * 32768f)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            destination.put(i, pcm.toShort())
        }
        output.position(bytes)
        output.flip()
    }

    private fun filterSample(channel: Int, input: Float): Float {
        var value = input
        for (filter in channelFilters[channel]) value = filter.process(value)
        return value
    }

    private fun configureFilters(profile: SubwooferCalibrationProfile?) {
        configuredProfile = profile
        val points = profile?.points.orEmpty().filter {
            it.frequencyHz > 0 && it.frequencyHz < sampleRateHz / 2
        }
        channelFilters = if (points.isEmpty()) {
            emptyArray()
        } else {
            Array(channelCount) {
                points.map { point ->
                    val gainDb = (point.lastCleanDbfs - (profile?.safetyMarginDb ?: 4f))
                        .coerceIn(-18f, 0f)
                    PeakingFilter(sampleRateHz, point.frequencyHz.toFloat(), 5f, gainDb)
                }.toTypedArray()
            }
        }
    }

    private class PeakingFilter(
        sampleRate: Int,
        frequencyHz: Float,
        q: Float,
        gainDb: Float
    ) {
        private val b0: Float
        private val b1: Float
        private val b2: Float
        private val a1: Float
        private val a2: Float
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        init {
            val omega = 2.0 * PI * frequencyHz / sampleRate
            val cosine = cos(omega)
            val alpha = sin(omega) / (2.0 * q)
            val amplitude = 10.0.pow(gainDb / 40.0)
            val a0 = 1.0 + alpha / amplitude
            b0 = ((1.0 + alpha * amplitude) / a0).toFloat()
            b1 = ((-2.0 * cosine) / a0).toFloat()
            b2 = ((1.0 - alpha * amplitude) / a0).toFloat()
            a1 = ((-2.0 * cosine) / a0).toFloat()
            a2 = ((1.0 - alpha / amplitude) / a0).toFloat()
        }

        fun process(input: Float): Float {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }
    }
}
