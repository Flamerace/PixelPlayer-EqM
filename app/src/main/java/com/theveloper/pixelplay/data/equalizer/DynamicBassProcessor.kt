// DynamicBassProcessor.kt
package com.theveloper.pixelplay.data.equalizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class DynamicBassProcessor : BaseAudioProcessor() {

    private var engine = DynamicBassEngine(44100f) // placeholder until real format arrives
    private var widener = StereoWidenerEngine(44100f)
    private var isEnabled = true
    private var widenerEnabled = false

    // Cached values so they survive engine rebuilds in onConfigure().
    // These mirror the eel preset's own @init defaults (dBassGain=45, dX1=1200,
    // dX2=6200, dSideGainX=0, dY1=40, dY2=80, dSideGainY=30) — the previous
    // values here (0 / 20 / 200 / 20 / 200 / 0 / 0) kept lowFreqX at 20Hz, which
    // is below DynamicBassEngine's 120Hz threshold, so the engine never left its
    // "simple mode" fallback and the real two-band Dynamic Bass algorithm never
    // actually ran — that mismatch, not the DSP math itself, was why the effect
    // didn't sound like ViperFX's Dynamic Bass out of the box.
    private var bassGain = 45f
    private var xLow = 1200f; private var xHigh = 6200f
    private var yLow = 40f; private var yHigh = 80f
    private var gx = 0f; private var gy = 30f

    // Stereo widener cached values.
    private var widthPercent = 100f // 100 = original image, unchanged
    private var bassProtectHz = 200f

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

    fun setWidenerEnabled(enabled: Boolean) { widenerEnabled = enabled }

    /** percent: 0 = mono, 100 = original stereo image, up to 200 = extra wide. */
    fun setStereoWidth(percent: Float) {
        widthPercent = percent
        widener.setWidth(percent)
    }

    /** Frequency below which the stereo image is left untouched by widening. */
    fun setBassProtectFrequency(freqHz: Float) {
        bassProtectHz = freqHz
        widener.setBassProtectFrequency(freqHz)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            // Unsupported format for this effect (e.g. mono, float/Hi-Fi output) —
            // become inactive instead of throwing and killing playback.
            return AudioProcessor.AudioFormat.NOT_SET
        }
        engine = DynamicBassEngine(inputAudioFormat.sampleRate.toFloat()).apply {
            setBassGain(bassGain)
            setFilterXPassFrequency(xLow, xHigh)
            setFilterYPassFrequency(yLow, yHigh)
            setSideGain(gx, gy)
        }
        widener = StereoWidenerEngine(inputAudioFormat.sampleRate.toFloat()).apply {
            setWidth(widthPercent)
            setBassProtectFrequency(bassProtectHz)
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
                var sampleL = engine.getLeft()
                var sampleR = engine.getRight()

                if (widenerEnabled) {
                    widener.process(sampleL, sampleR)
                    sampleL = widener.getLeft()
                    sampleR = widener.getRight()
                }

                // DynamicBassEngine (and the widener, when enabled) apply their own
                // scoped gain-reduction limiters internally, so this is just the
                // float-to-int16 conversion. Round to nearest instead of truncating
                // toward zero — truncation biases every sample toward zero, which
                // adds a small but consistent quantization distortion.
                val outL = (sampleL * Short.MAX_VALUE).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                val outR = (sampleR * Short.MAX_VALUE).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

                outputSamples.put(i * 2, outL.toShort())
                outputSamples.put(i * 2 + 1, outR.toShort())
            }
        }

        inputBuffer.position(inputBuffer.limit())
        outputBuffer.position(frames * 2 * 2)
        outputBuffer.flip() // CRITICAL: mark exactly the bytes written as valid/readable output
    }
}



/*package com.theveloper.pixelplay.data.equalizer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

class DynamicBassProcessor : BaseAudioProcessor() {

    private var engine = DynamicBassEngine(44100f) // placeholder until real format arrives
    private var isEnabled = true

    // Cached values so they survive engine rebuilds in onConfigure()
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
            // Unsupported format for this effect (e.g. mono, float/Hi-Fi output) —
            // become inactive instead of throwing and killing playback.
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

                // DynamicBassEngine now applies its own scoped gain-reduction limiter
                // internally, so this is just a safety clamp for int16 conversion.
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
        outputBuffer.flip() // CRITICAL: mark exactly the bytes written as valid/readable output
    }
}*/

