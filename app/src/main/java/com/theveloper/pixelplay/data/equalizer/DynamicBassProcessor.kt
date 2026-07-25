package com.theveloper.pixelplay.data.equalizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

class DynamicBassProcessor(
    private val sampleRate: Int
) : BaseAudioProcessor() {

    private val engine = DynamicBassEngine(sampleRate.toFloat())
    private var isEnabled = true

    fun setEnabled(enabled: Boolean) { isEnabled = enabled }
    fun setBassGain(gain: Float) = engine.setBassGain(gain)
    fun setFilterX(low: Float, high: Float) = engine.setFilterXPassFrequency(low, high)
    fun setFilterY(low: Float, high: Float) = engine.setFilterYPassFrequency(low, high)
    fun setSideGain(gx: Float, gy: Float) = engine.setSideGain(gx, gy)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            C.ENCODING_PCM_16BIT
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val totalSamples = inputBuffer.remaining() / 2 // 2 bytes per 16-bit sample
        if (totalSamples == 0) return

        val frames = totalSamples / 2 // stereo: 2 samples per frame
        val inputSamples = inputBuffer.asShortBuffer()

        val outputBuffer = replaceOutputBuffer(frames * 2 * 2) // frames * channels * bytesPerSample
        val outputSamples = outputBuffer.asShortBuffer()

        if (!isEnabled) {
            for (i in 0 until frames * 2) {
                outputSamples.put(i, inputSamples.get(i))
            }
        } else {
            for (i in 0 until frames) {
                val left = inputSamples.get(i * 2).toFloat() / Short.MAX_VALUE
                val right = inputSamples.get(i * 2 + 1).toFloat() / Short.MAX_VALUE

                engine.processSamples(left, right)

                val outL = (engine.getLeft() * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                val outR = (engine.getRight() * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

                outputSamples.put(i * 2, outL.toShort())
                outputSamples.put(i * 2 + 1, outR.toShort())
            }
        }

        inputBuffer.position(inputBuffer.limit())
        outputBuffer.position(frames * 2 * 2)
    }
}
