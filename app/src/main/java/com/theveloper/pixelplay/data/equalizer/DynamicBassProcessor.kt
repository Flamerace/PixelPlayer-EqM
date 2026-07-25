package com.theveloper.pixelplay.data.equalizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

class DynamicBassProcessor(
    private var engine = DynamicBassEngine(44100f) // placeholder until real format arrives
    private var isEnabled = true
    // cache last-set values so they survive engine rebuilds
    private var bassGain = 0f; private var xLow = 20f; private var xHigh = 200f
    private var yLow = 20f; private var yHigh = 200f; private var gx = 0f; private var gy = 0f

    fun setEnabled(e: Boolean) { isEnabled = e }
    fun setBassGain(g: Float) { bassGain = g; engine.setBassGain(g) }
    fun setFilterX(l: Float, h: Float) { xLow = l; xHigh = h; engine.setFilterXPassFrequency(l, h) }
    fun setFilterY(l: Float, h: Float) { yLow = l; yHigh = h; engine.setFilterYPassFrequency(l, h) }
    fun setSideGain(x: Float, y: Float) { gx = x; gy = y; engine.setSideGain(x, y) }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        engine = DynamicBassEngine(inputAudioFormat.sampleRate.toFloat()).apply {
            setBassGain(bassGain); setFilterXPassFrequency(xLow, xHigh)
            setFilterYPassFrequency(yLow, yHigh); setSideGain(gx, gy)
        }
        return inputAudioFormat
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
