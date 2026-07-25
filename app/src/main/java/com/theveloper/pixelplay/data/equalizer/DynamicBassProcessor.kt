package com.theveloper.pixelplay.data.equalizer

import androidx.media3.common.AudioFormat
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

    override fun configure(inputFormat: AudioFormat, outputFormat: AudioFormat): AudioFormat? {
        if (inputFormat.pcmEncoding != AudioFormat.ENCODING_PCM_16BIT || inputFormat.channelCount != 2) {
            return null
        }
        return AudioFormat.Builder()
            .setSampleRate(inputFormat.sampleRate)
            .setChannelCount(inputFormat.channelCount)
            .setPcmEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
    }

    override fun process(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer): Boolean {
        if (!isEnabled) {
            outputBuffer.put(inputBuffer)
            return true
        }

        val inputSamples = inputBuffer.asShortBuffer()
        val totalSamples = inputSamples.remaining()
        if (totalSamples == 0) return false

        val frames = totalSamples / 2
        val outputSamples = outputBuffer.asShortBuffer()

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

        inputBuffer.position(inputBuffer.limit())
        outputBuffer.position(outputBuffer.position() + totalSamples * 2)
        return true
    }
}
