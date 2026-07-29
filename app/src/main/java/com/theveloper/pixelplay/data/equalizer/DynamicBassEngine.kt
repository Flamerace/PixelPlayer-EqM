// DynamicBassEngine.kt
package com.theveloper.pixelplay.data.equalizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Low-pass 2-pole IIR filter. Used only for the "simple mode" fallback
 * (see [DynamicBassEngine.processSamples]) — a direct port of the eel
 * LowPassFilter_Set / LowPassFilter_ProcessSample functions.
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
 * 4-pole cascaded one-pole filter producing three simultaneous outputs
 * (low band, high band via delay-compensated subtraction, and the band
 * between them). Direct port of the eel PolesFilter_Set / ProcessSample
 * functions.
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
 * Main Dynamic Bass engine — a port of ViperFX's "Dynamic Bass" eel effect.
 *
 * The summing here now matches the reference exactly:
 *   out = xL1 + xL2 + yL2 + sideGainX*yL1 + sideGainY*yL0
 * i.e. the original signal (mids via xL2, highs via xL1) and the bass core
 * (yL2) are always present at unity — sideGainX/sideGainY only ever ADD
 * emphasis (kick/drum punch, sub-bass) on top of that, they never gate or
 * duck anything. That's what the reference relies on for a "bunchy, felt"
 * low end instead of an airy/muddy one: nothing about the original mix is
 * ever removed or ducked to make room for the boost.
 *
 * Reworked on top of that reference behavior to add ONE thing the eel
 * effect doesn't have: the boost itself now follows a fast-attack /
 * slow-release envelope of the incoming bass-band energy, so it swells in
 * on a hard hit and settles back down on quiet or sustained low rumble,
 * rather than sitting at a constant level. This only scales the ADDED
 * portions (the sideGainX/sideGainY terms) — the always-on xL1/xL2/yL2
 * unity path is untouched, so the source material's own bass core is
 * never pumped or gated, only the extra "hit" emphasis is.
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

    // --- Hit envelope (drives the dynamic "felt on hits" behavior) ---
    // Tracks the energy of yL1+yL2 (the "kick/bassy-drum" and "bass core" bands —
    // i.e. actual bass hits, not the sub-40Hz rumble in yL0) with a fast attack so a
    // transient is felt essentially immediately, and a slow release so it doesn't
    // pump or flutter on sustained bass, and doesn't react to slow, continuous low
    // rumble the way a hit-detector shouldn't. drive is a normalized 0..~1.3 read of
    // "how hard is a bass hit landing right now", used to scale sideGainX/sideGainY.
    private var hitEnvelope = 0f
    private val hitAttackCoeff = exp(-1f / (sampleRate * 0.006f))   // ~6ms — catch the transient
    private val hitReleaseCoeff = exp(-1f / (sampleRate * 0.180f))  // ~180ms — settle, no pumping
    private var hitReference = 0.2f // auto-calibrated toward recent peak hits, see updateHitReference

    // Baseline level of "added emphasis" even when nothing is actively hitting, so the
    // effect doesn't drop to silence between hits — only swells further ON hits. 1.0
    // would mean full sideGain even at rest; kept modest so the swell is audible.
    private val restDrive = 0.35f

    // Absolute last resort: guarantees no output sample can ever exceed true full
    // scale. The reference effect has no limiter at all and relies on host gain
    // staging; this keeps 16-bit PCM output from hard-clipping without altering tone
    // (it only engages on genuine overs, and releases quickly once clear).
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
            // Simple mode: matches the reference exactly (out = left/right + boosted
            // band, no ducking) — the boosted band itself is still hit-driven below.
            val avg = lowPass.process(left + right)
            val drive = updateHitEnvelope(abs(avg))

            outSampleL = left + avg * drive
            outSampleR = right + avg * drive
        } else {
            filterXL.process(left)
            filterXR.process(right)
            val xL0 = filterXL.getOut0(); val xR0 = filterXR.getOut0()
            val xL1 = filterXL.getOut1(); val xR1 = filterXR.getOut1() // highs — always unity
            val xL2 = filterXL.getOut2(); val xR2 = filterXR.getOut2() // mids — always unity

            filterYL.process(bassGain * xL0)
            filterYR.process(bassGain * xR0)
            val yL0 = filterYL.getOut0(); val yR0 = filterYR.getOut0() // sub-bass — added, envelope-scaled
            val yL1 = filterYL.getOut1(); val yR1 = filterYR.getOut1() // kicks/bassy drums — added, envelope-scaled
            val yL2 = filterYL.getOut2(); val yR2 = filterYR.getOut2() // bass core — always unity

            // Hit envelope reads the actual bass-hit content (kick/drum band + bass
            // core), not the raw sub-40Hz rumble alone, so continuous low rumble
            // without transients doesn't keep the effect maxed out.
            val hitLevel = max(abs(yL1) + abs(yL2), abs(yR1) + abs(yR2))
            val drive = updateHitEnvelope(hitLevel)

            // Reference summing, unchanged: xL1 + xL2 + yL2 always present at unity.
            // sideGainX/sideGainY only ever ADD emphasis on top — now scaled by the
            // hit envelope so that emphasis swells on a hit and eases off at rest,
            // instead of sitting at a fixed level.
            outSampleL = xL1 + xL2 + yL2 + (sideGainX * drive) * yL1 + (sideGainY * drive) * yL0
            outSampleR = xR1 + xR2 + yR2 + (sideGainX * drive) * yR1 + (sideGainY * drive) * yR0
        }

        // Safety only — no tonal ducking, matches the reference's "no limiter"
        // philosophy as closely as possible while still protecting PCM output.
        val finalPeak = max(abs(outSampleL), abs(outSampleR))
        val targetSafetyGain = if (finalPeak > hardCeiling) hardCeiling / finalPeak else 1f
        safetyGainReduction = updateGain(safetyGainReduction, targetSafetyGain, safetyAttackCoeff, safetyReleaseCoeff)
        outSampleL *= safetyGainReduction
        outSampleR *= safetyGainReduction
    }

    /**
     * Updates the fast-attack/slow-release hit envelope from [level] and returns the
     * normalized drive (restDrive..~1.3) to scale the added emphasis by. Also nudges
     * hitReference toward recent peaks so "what counts as a hard hit" adapts to the
     * track/gain settings instead of needing manual tuning per song.
     */
    private fun updateHitEnvelope(level: Float): Float {
        hitEnvelope = if (level > hitEnvelope) {
            hitAttackCoeff * hitEnvelope + (1f - hitAttackCoeff) * level
        } else {
            hitReleaseCoeff * hitEnvelope + (1f - hitReleaseCoeff) * level
        }
        // Slow adaptation of the reference ceiling toward recent peaks (minutes-scale),
        // so "hard hit" stays meaningful across tracks mixed at different loudness
        // without the user having to retune anything.
        if (hitEnvelope > hitReference) {
            hitReference = 0.999995f * hitReference + 0.000005f * hitEnvelope
        } else {
            hitReference = 0.99999f * hitReference + 0.00001f * hitReference.coerceAtLeast(0.05f)
        }
        val normalized = (hitEnvelope / hitReference).coerceIn(0f, 1.3f)
        return restDrive + (1f - restDrive) * normalized
    }

    private fun updateGain(current: Float, target: Float, attack: Float, release: Float): Float {
        val coeff = if (target < current) attack else release
        return coeff * current + (1f - coeff) * target
    }

    fun getLeft() = outSampleL
    fun getRight() = outSampleR
}
