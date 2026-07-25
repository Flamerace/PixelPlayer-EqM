package com.theveloper.pixelplay.data.equalizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

class DynamicBassProcessor : BaseAudioProcessor() {

    private var engine = DynamicBassEngine(44100f) // placeholder until real format arrives
    private var isEnabled = true

    // cached values so they survive engine rebuilds in onConfigure()
    private var bassGain = 0f
    private var xLow = 20f; private var xHigh = 200f
    private var yLow = 20f; private var yHigh = 200f
    private var gx = 0f; private var gy = 0f

    fun setEnabled(enabled: Boolean) { isEnabled = enabled }

    fun setBassGain(gain: Float) {
        bassGain = gain
        engine.setBassGain(gain)
    }

    fun setFilterX(low: Float, high: Float) {
        xLow = low; xHigh = high
        engine.setFilterXPassFrequency(low, high)
    }

    fun setFilterY(low: Float, high: Float) {
        yLow = low; yHigh = high
        engine.setFilterYPassFrequency(low, high)
    }

    fun setSideGain(gainX: Float, gainY: Float) {
        gx = gainX; gy = gainY
        engine.setSideGain(gainX, gainY)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
        // Unsupported format for this effect — become inactive instead of failing playback
        return AudioProcessor.AudioFormat.NOT_SET
    }
    engine = DynamicBassEngine(inputAudioFormat.sampleRate.toFloat()).apply {
        setBassGain(bassGain)
        setFilterXPassFrequency(xLow, xHigh)
        setFilterYPassFrequency(yLow, yHigh)
        setSideGain(gx, gy)
    }
    return AudioProcessor.AudioFormat(
        inputAudioFormat.sampleRate,
        inputAudioFormat.channelCount,
        C.ENCODING_PCM_16BIT
    )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
    val totalSamples = inputBuffer.remaining() / 2
    if (totalSamples == 0) return

    val frames = totalSamples / 2
    val inputSamples = inputBuffer.asShortBuffer()

    val outputBuffer = replaceOutputBuffer(frames * 2 * 2)
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

            // soft-clip instead of hard-clamp to avoid digital clipping artifacts
            val outL = (tanh(engine.getLeft()) * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val outR = (tanh(engine.getRight()) * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            outputSamples.put(i * 2, outL.toShort())
            outputSamples.put(i * 2 + 1, outR.toShort())
        }
    }

    inputBuffer.position(inputBuffer.limit())
    outputBuffer.position(frames * 2 * 2)
    }
}
