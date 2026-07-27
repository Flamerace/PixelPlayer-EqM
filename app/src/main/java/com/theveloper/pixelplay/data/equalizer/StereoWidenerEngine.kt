// StereoWidenerEngine.kt
package com.theveloper.pixelplay.data.equalizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * One-pole RC-style high-pass filter, used to split the mid/side "side"
 * signal into a protected low band and a widenable high band.
 */
internal class OnePoleHighPass(private val sampleRate: Float) {
    private var alpha = 0f
    private var prevIn = 0f
    private var prevOut = 0f

    fun set(cutoffHz: Float) {
        alpha = sampleRate / (sampleRate + 2f * PI.toFloat() * cutoffHz)
        prevIn = 0f; prevOut = 0f
    }

    fun process(x: Float): Float {
        val y = alpha * (prevOut + x - prevIn)
        prevIn = x
        prevOut = y
        return y
    }
}

/**
 * Mid/side stereo widener.
 *
 * Splits L/R into mid = (L+R)/2 and side = (L-R)/2, then scales only the
 * side signal above [bassProtectFreq] by [width]. Content below that
 * frequency is passed through untouched, so bass content — where phase
 * differences between channels are most audible as cancellation on mono
 * speakers/summed playback — is never exaggerated by the widening.
 *
 * width = 1.0 reproduces the original signal exactly (mid ± side = L/R,
 * bit-identical up to filter/float rounding). width < 1.0 narrows the
 * image toward mono; width > 1.0 widens it.
 */
class StereoWidenerEngine(private val sampleRate: Float) {
    private val sideHighPass = OnePoleHighPass(sampleRate)

    private var width = 1f
    private var outL = 0f
    private var outR = 0f

    // Light safety limiter: widening can push the reconstructed L/R peak
    // above the pre-widening peak, so guard against clipping the same way
    // DynamicBassEngine does.
    private val ceiling = 0.999f
    private val attackCoeff = exp(-1f / (sampleRate * 0.001f))  // ~1ms
    private val releaseCoeff = exp(-1f / (sampleRate * 0.100f)) // ~100ms
    private var safetyGainReduction = 1f

    init {
        sideHighPass.set(200f) // default bass-protect cutoff
    }

    /** percent: 0 = mono, 100 = original stereo image, up to 200 = extra wide. */
    fun setWidth(percent: Float) {
        width = (percent * 0.01f).coerceIn(0f, 2f)
    }

    fun setBassProtectFrequency(freqHz: Float) {
        sideHighPass.set(freqHz)
    }

    fun process(left: Float, right: Float) {
        val mid = (left + right) * 0.5f
        val side = (left - right) * 0.5f

        val sideHigh = sideHighPass.process(side)
        val sideLow = side - sideHigh
        val widenedSide = sideLow + sideHigh * width

        outL = mid + widenedSide
        outR = mid - widenedSide

        val peak = max(abs(outL), abs(outR))
        val targetGain = if (peak > ceiling) ceiling / peak else 1f
        safetyGainReduction = if (targetGain < safetyGainReduction) {
            attackCoeff * safetyGainReduction + (1f - attackCoeff) * targetGain
        } else {
            releaseCoeff * safetyGainReduction + (1f - releaseCoeff) * targetGain
        }
        outL *= safetyGainReduction
        outR *= safetyGainReduction
    }

    fun getLeft() = outL
    fun getRight() = outR
}
