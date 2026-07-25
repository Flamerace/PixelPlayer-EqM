// DynamicBassProcessor.kt
package com.theveloper.pixelplay.data.equalizer

import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class DynamicBassProcessor(
    private val sampleRate: Int  // from the player’s audio format
) : BaseAudioProcessor() {

    private val engine = DynamicBassEngine(sampleRate.toFloat())
    private var channelCount = 0
    private var isEnabled = true

    // All parameters are kept in the engine; we just forward updates.
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    fun setBassGain(gain: Float) = engine.setBassGain(gain)
    fun setFilterX(low: Float, high: Float) = engine.setFilterXPassFrequency(low, high)
    fun setFilterY(low: Float, high: Float) = engine.setFilterYPassFrequency(low, high)
    fun setSideGain(gx: Float, gy: Float) = engine.setSideGain(gx, gy)

    override fun configure(
        sampleRateHz: Int,
        channelCount: Int,
        encoding: Int
    ): AudioFormat? {
        // Only process 16‑bit PCM, stereo is expected for this effect.
        if (encoding != AudioFormat.ENCODING_PCM_16BIT || channelCount != 2) {
            return null
        }
        this.channelCount = channelCount
        return AudioFormat(sampleRateHz, channelCount, encoding)
    }

    override fun process(buffer: ByteBuffer): Boolean {
        if (!isEnabled) {
            // Bypass: do nothing, just advance position
            buffer.position(buffer.position() + buffer.remaining())
            return false
        }

        val samples = buffer.asShortBuffer()
        val totalSamples = samples.remaining()
        if (totalSamples == 0) return false

        // Process each stereo frame
        val frames = totalSamples / 2
        for (i in 0 until frames) {
            val left = samples.get(i * 2).toFloat() / Short.MAX_VALUE
            val right = samples.get(i * 2 + 1).toFloat() / Short.MAX_VALUE

            engine.processSamples(left, right)

            val outL = (engine.getLeft() * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            val outR = (engine.getRight() * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            samples.put(i * 2, outL.toShort())
            samples.put(i * 2 + 1, outR.toShort())
        }

        // Consume the input buffer (we wrote in‑place)
        buffer.position(buffer.position() + buffer.remaining())
        return true
    }

    override fun reset() {
        super.reset()
        // If needed, re‑init the engine (but preserve parameters).
        // We rely on the engine’s state; no special reset needed.
    }
}