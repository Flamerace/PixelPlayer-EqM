// DynamicBassEngine.kt
/*package com.theveloper.pixelplay.data.equalizer

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.cos

/**
 * Low-pass 2‑pole IIR filter.
 */
internal class LowPassFilter(private val sampleRate: Float) {
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private var x1 = 0f; private var x2 = 0f
    private var y1 = 0f; private var y2 = 0f

    fun set(frequency: Float, qFactor: Float) {
        val x = (frequency * 2f * PI.toFloat()) / sampleRate
        val sinX = sin(x)
        val cosX = cos(x)
        val y = sinX / (qFactor * 2f)

        val a0 = y + 1f
        val a1 = cosX * -2f
        val a2 = 1f - y
        val b0 = (1f - cosX) / 2f
        val b1 = 1f - cosX
        val b2 = b0

        this.b0 = b0 / a0
        this.b1 = b1 / a0
        this.b2 = b2 / a0
        this.a1 = -a1 / a0
        this.a2 = -a2 / a0

        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }

    fun process(sample: Float): Float {
        val out = sample * b0 + x1 * b1 + x2 * b2 + y1 * a1 + y2 * a2
        y2 = y1; y1 = out
        x2 = x1; x1 = sample
        return out
    }
}

/**
 * 4‑pole filter (two cascaded biquads) producing three outputs.
 */
internal class PolesFilter(private val sampleRate: Float) {
    private var input0 = 0f; private var input1 = 0f; private var input2 = 0f
    private var x0 = 0f; private var x1 = 0f; private var x2 = 0f; private var x3 = 0f
    private var y0 = 0f; private var y1 = 0f; private var y2 = 0f; private var y3 = 0f
    private var lowerAngle = 0f; private var upperAngle = 0f
    private var out0 = 0f; private var out1 = 0f; private var out2 = 0f

    fun set(lowerFreq: Float, upperFreq: Float) {
        input0 = 0f; input1 = 0f; input2 = 0f
        x0 = 0f; x1 = 0f; x2 = 0f; x3 = 0f
        y0 = 0f; y1 = 0f; y2 = 0f; y3 = 0f
        lowerAngle = (lowerFreq * PI.toFloat()) / sampleRate
        upperAngle = (upperFreq * PI.toFloat()) / sampleRate
    }

    fun process(sample: Float) {
        val oldestSampleIn = input2
        input2 = input1; input1 = input0; input0 = sample

        x0 += lowerAngle * (sample - x0)
        x1 += lowerAngle * (x0 - x1)
        x2 += lowerAngle * (x1 - x2)
        x3 += lowerAngle * (x2 - x3)

        y0 += upperAngle * (sample - y0)
        y1 += upperAngle * (y0 - y1)
        y2 += upperAngle * (y1 - y2)
        y3 += upperAngle * (y2 - y3)

        out0 = x3
        out1 = oldestSampleIn - y3
        out2 = y3 - x3
    }

    fun getOut0() = out0
    fun getOut1() = out1
    fun getOut2() = out2
}

/**
 * Main Dynamic Bass engine.
 */
class DynamicBassEngine(private val sampleRate: Float) {
    private val lowPass = LowPassFilter(sampleRate)
    private val filterXL = PolesFilter(sampleRate)
    private val filterXR = PolesFilter(sampleRate)
    private val filterYL = PolesFilter(sampleRate)
    private val filterYR = PolesFilter(sampleRate)

    private var bassGain = 1f
    private var sideGainX = 1f
    private var sideGainY = 1f
    private var lowFreqX = 120f
    private var highFreqX = 80f
    private var lowFreqY = 40f
    private var highFreqY = sampleRate / 4f
    private var qPeak = 0f
    private var outSampleL = 0f
    private var outSampleR = 0f

    init {
        initFilters()
    }

    private fun initFilters() {
        filterXL.set(lowFreqX, highFreqX)
        filterXR.set(lowFreqX, highFreqX)
        filterYL.set(lowFreqY, highFreqY)
        filterYR.set(lowFreqY, highFreqY)
        lowPass.set(55f, qPeak / 666f + 0.5f)
    }

    fun setBassGain(gainPercent: Float) { // 0..100
        bassGain = (gainPercent * 20 + 100) * 0.01f
        qPeak = (bassGain - 1f) / 20f * 1600f
        if (qPeak > 1600f) qPeak = 1600f
        lowPass.set(55f, qPeak / 666f + 0.5f)
    }

    fun setFilterXPassFrequency(low: Float, high: Float) {
        lowFreqX = low
        highFreqX = high
        filterXL.set(low, high)
        filterXR.set(low, high)
        lowPass.set(55f, qPeak / 666f + 0.5f)
    }

    fun setFilterYPassFrequency(low: Float, high: Float) {
        lowFreqY = low
        highFreqY = high
        filterYL.set(low, high)
        filterYR.set(low, high)
        lowPass.set(55f, qPeak / 666f + 0.5f)
    }

    fun setSideGain(gainX: Float, gainY: Float) { // 0..100 → 0..1
        sideGainX = gainX * 0.01f
        sideGainY = gainY * 0.01f
    }

    fun processSamples(left: Float, right: Float) {
        if (lowFreqX <= 120f) {
            val avg = lowPass.process(left + right)
            outSampleL = left + avg
            outSampleR = right + avg
        } else {
            filterXL.process(left)
            filterXR.process(right)
            val xL0 = filterXL.getOut0(); val xR0 = filterXR.getOut0()
            val xL1 = filterXL.getOut1(); val xR1 = filterXR.getOut1()
            val xL2 = filterXL.getOut2(); val xR2 = filterXR.getOut2()

            filterYL.process(bassGain * xL0)
            filterYR.process(bassGain * xR0)
            val yL0 = filterYL.getOut0(); val yR0 = filterYR.getOut0()
            val yL1 = filterYL.getOut1(); val yR1 = filterYR.getOut1()
            val yL2 = filterYL.getOut2(); val yR2 = filterYR.getOut2()

            outSampleL = xL1 + yL2 + sideGainX * yL1 + sideGainY * yL0 + xL2
            outSampleR = xR1 + yR2 + sideGainX * yR1 + sideGainY * yR0 + xR2
        }
    }

    fun getLeft() = outSampleL
    fun getRight() = outSampleR
}*/

// DynamicBassEngine.kt
package com.theveloper.pixelplay.data.equalizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Low-pass 2‑pole IIR filter.
 */
internal class LowPassFilter(private val sampleRate: Float) {
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private var x1 = 0f; private var x2 = 0f
    private var y1 = 0f; private var y2 = 0f

    fun set(frequency: Float, qFactor: Float) {
        val x = (frequency * 2f * PI.toFloat()) / sampleRate
        val sinX = sin(x)
        val cosX = cos(x)
        val y = sinX / (qFactor * 2f)

        val a0 = y + 1f
        val a1 = cosX * -2f
        val a2 = 1f - y
        val b0 = (1f - cosX) / 2f
        val b1 = 1f - cosX
        val b2 = b0

        this.b0 = b0 / a0
        this.b1 = b1 / a0
        this.b2 = b2 / a0
        this.a1 = -a1 / a0
        this.a2 = -a2 / a0

        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }

    fun process(sample: Float): Float {
        val out = sample * b0 + x1 * b1 + x2 * b2 + y1 * a1 + y2 * a2
        y2 = y1; y1 = out
        x2 = x1; x1 = sample
        return out
    }
}

/**
 * 4‑pole filter (two cascaded biquads) producing three outputs.
 */
internal class PolesFilter(private val sampleRate: Float) {
    private var input0 = 0f; private var input1 = 0f; private var input2 = 0f
    private var x0 = 0f; private var x1 = 0f; private var x2 = 0f; private var x3 = 0f
    private var y0 = 0f; private var y1 = 0f; private var y2 = 0f; private var y3 = 0f
    private var lowerAngle = 0f; private var upperAngle = 0f
    private var out0 = 0f; private var out1 = 0f; private var out2 = 0f

    fun set(lowerFreq: Float, upperFreq: Float) {
        input0 = 0f; input1 = 0f; input2 = 0f
        x0 = 0f; x1 = 0f; x2 = 0f; x3 = 0f
        y0 = 0f; y1 = 0f; y2 = 0f; y3 = 0f
        lowerAngle = (lowerFreq * PI.toFloat()) / sampleRate
        upperAngle = (upperFreq * PI.toFloat()) / sampleRate
    }

    fun process(sample: Float) {
        val oldestSampleIn = input2
        input2 = input1; input1 = input0; input0 = sample

        x0 += lowerAngle * (sample - x0)
        x1 += lowerAngle * (x0 - x1)
        x2 += lowerAngle * (x1 - x2)
        x3 += lowerAngle * (x2 - x3)

        y0 += upperAngle * (sample - y0)
        y1 += upperAngle * (y0 - y1)
        y2 += upperAngle * (y1 - y2)
        y3 += upperAngle * (y2 - y3)

        out0 = x3
        out1 = oldestSampleIn - y3
        out2 = y3 - x3
    }

    fun getOut0() = out0
    fun getOut1() = out1
    fun getOut2() = out2
}

/**
 * Main Dynamic Bass engine.
 */
class DynamicBassEngine(private val sampleRate: Float) {
    private val lowPass = LowPassFilter(sampleRate)
    private val filterXL = PolesFilter(sampleRate)
    private val filterXR = PolesFilter(sampleRate)
    private val filterYL = PolesFilter(sampleRate)
    private val filterYR = PolesFilter(sampleRate)

    private var bassGain = 1f
    private var sideGainX = 1f
    private var sideGainY = 1f
    private var lowFreqX = 120f
    private var highFreqX = 80f
    private var lowFreqY = 40f
    private var highFreqY = sampleRate / 4f
    private var qPeak = 0f
    private var outSampleL = 0f
    private var outSampleR = 0f

    // --- Peak limiter state ---
    // Two independent, priority-ordered ducking stages:
    //   primary   = mids (X1-X2) + kicks/bassy-drums (Y2-X1) — ducked first, and fully as needed
    //   secondary = highs (X2 and above) — left alone unless primary ducking alone isn't enough,
    //               and even then only partially ducked (gentle, not a hard limit)
    // The bass core (Y1-Y2) and sub-bass (below Y1) are never part of either stage.
    private val limiterCeiling = 0.98f
    private val attackCoeff = exp(-1f / (sampleRate * 0.005f))    // ~5ms — catch hits fast
    private val releaseCoeff = exp(-1f / (sampleRate * 0.100f))  // ~100ms — recover smoothly
    // Secondary (highs) reacts a little slower and only ever applies half the needed reduction,
    // so it reads as a light touch rather than an audible duck.
    private val secondaryAttackCoeff = exp(-1f / (sampleRate * 0.015f))  // ~15ms
    private val secondaryReleaseCoeff = exp(-1f / (sampleRate * 0.150f)) // ~150ms
    private val secondaryDuckBlend = 0.5f
    private var primaryGainReduction = 1f
    private var secondaryGainReduction = 1f

    // Absolute last resort: guarantees no output sample can ever exceed true full scale,
    // even in the pathological edge case where the preserved bass core alone (which the
    // two stages above never touch) is hot enough to overflow on its own. In normal use
    // this should essentially never engage — stages 1 and 2 handle everything else.
    private var safetyGainReduction = 1f
    private val safetyAttackCoeff = exp(-1f / (sampleRate * 0.001f))  // ~1ms — near-instant
    private val safetyReleaseCoeff = exp(-1f / (sampleRate * 0.100f)) // ~100ms
    private val hardCeiling = 0.999f

    init {
        initFilters()
    }

    private fun initFilters() {
        filterXL.set(lowFreqX, highFreqX)
        filterXR.set(lowFreqX, highFreqX)
        filterYL.set(lowFreqY, highFreqY)
        filterYR.set(lowFreqY, highFreqY)
        lowPass.set(55f, qPeak / 666f + 0.5f)
    }

    fun setBassGain(gainPercent: Float) { // 0..100
        bassGain = (gainPercent * 20 + 100) * 0.01f
        qPeak = (bassGain - 1f) / 20f * 1600f
        if (qPeak > 1600f) qPeak = 1600f
        lowPass.set(55f, qPeak / 666f + 0.5f)
    }

    fun setFilterXPassFrequency(low: Float, high: Float) {
        lowFreqX = low
        highFreqX = high
        filterXL.set(low, high)
        filterXR.set(low, high)
        lowPass.set(55f, qPeak / 666f + 0.5f)
    }

    fun setFilterYPassFrequency(low: Float, high: Float) {
        lowFreqY = low
        highFreqY = high
        filterYL.set(low, high)
        filterYR.set(low, high)
        lowPass.set(55f, qPeak / 666f + 0.5f)
    }

    fun setSideGain(gainX: Float, gainY: Float) { // 0..100 → 0..1
        sideGainX = gainX * 0.01f
        sideGainY = gainY * 0.01f
    }

    fun processSamples(left: Float, right: Float) {
        if (lowFreqX <= 120f) {
            // Simple mode: a single resonant boosted band added on top of the original signal.
            // Duck only the added boost, never the original pass-through content.
            val avg = lowPass.process(left + right)

            val peak = max(abs(left + avg), abs(right + avg))
            primaryGainReduction = updateGain(primaryGainReduction, peak, attackCoeff, releaseCoeff)

            outSampleL = left + avg * primaryGainReduction
            outSampleR = right + avg * primaryGainReduction
        } else {
            filterXL.process(left)
            filterXR.process(right)
            val xL0 = filterXL.getOut0(); val xR0 = filterXR.getOut0()
            val xL1 = filterXL.getOut1(); val xR1 = filterXR.getOut1() // highs (X2+) — secondary, mostly untouched
            val xL2 = filterXL.getOut2(); val xR2 = filterXR.getOut2() // mids (X1-X2) — primary duck target

            filterYL.process(bassGain * xL0)
            filterYR.process(bassGain * xR0)
            val yL0 = filterYL.getOut0(); val yR0 = filterYR.getOut0() // sub-bass (below Y1) — preserved
            val yL1 = filterYL.getOut1(); val yR1 = filterYR.getOut1() // kicks/bassy drums (Y2-X1) — primary duck target
            val yL2 = filterYL.getOut2(); val yR2 = filterYR.getOut2() // bass core (Y1-Y2) — preserved

            // Preserved: never ducked, this is the whole point of the effect.
            val preservedL = yL2 + sideGainY * yL0
            val preservedR = yR2 + sideGainY * yR0

            // Primary duck target: mids + kick/drum band. Ducked first and fully as needed.
            val primaryL = xL2 + sideGainX * yL1
            val primaryR = xR2 + sideGainX * yR1

            // Secondary: highs above X2. Left alone unless primary ducking alone isn't enough.
            val secondaryL = xL1
            val secondaryR = xR1

            // Stage 1: duck the primary band to keep preserved+primary under the ceiling.
            val lowMidPeak = max(abs(preservedL + primaryL), abs(preservedR + primaryR))
            val targetPrimaryGain = if (lowMidPeak > limiterCeiling) {
                val preservedPeak = max(abs(preservedL), abs(preservedR))
                val primaryPeak = max(abs(primaryL), abs(primaryR))
                if (primaryPeak > 1e-6f) {
                    ((limiterCeiling - preservedPeak) / primaryPeak).coerceIn(0f, 1f)
                } else 1f
            } else 1f
            primaryGainReduction = updateGain(primaryGainReduction, targetPrimaryGain, attackCoeff, releaseCoeff)

            val stage1L = preservedL + primaryL * primaryGainReduction
            val stage1R = preservedR + primaryR * primaryGainReduction

            // Stage 2: only if still clipping after fully ducking primary, gently duck the highs too —
            // and only apply half the theoretically-needed reduction, so it stays subtle.
            val totalPeak = max(abs(stage1L + secondaryL), abs(stage1R + secondaryR))
            val targetSecondaryGain = if (totalPeak > limiterCeiling) {
                val stage1Peak = max(abs(stage1L), abs(stage1R))
                val secondaryPeak = max(abs(secondaryL), abs(secondaryR))
                val neededGain = if (secondaryPeak > 1e-6f) {
                    ((limiterCeiling - stage1Peak) / secondaryPeak).coerceIn(0f, 1f)
                } else 1f
                1f - (1f - neededGain) * secondaryDuckBlend
            } else 1f
            secondaryGainReduction = updateGain(
                secondaryGainReduction, targetSecondaryGain, secondaryAttackCoeff, secondaryReleaseCoeff
            )

            outSampleL = stage1L + secondaryL * secondaryGainReduction
            outSampleR = stage1R + secondaryR * secondaryGainReduction
        }

        // Final safety net, applied uniformly regardless of which branch ran above.
        val finalPeak = max(abs(outSampleL), abs(outSampleR))
        val targetSafetyGain = if (finalPeak > hardCeiling) hardCeiling / finalPeak else 1f
        safetyGainReduction = updateGain(safetyGainReduction, targetSafetyGain, safetyAttackCoeff, safetyReleaseCoeff)
        outSampleL *= safetyGainReduction
        outSampleR *= safetyGainReduction
    }

    private fun updateGain(current: Float, target: Float, attack: Float, release: Float): Float {
        val coeff = if (target < current) attack else release
        return coeff * current + (1f - coeff) * target
    }

    fun getLeft() = outSampleL
    fun getRight() = outSampleR
}
