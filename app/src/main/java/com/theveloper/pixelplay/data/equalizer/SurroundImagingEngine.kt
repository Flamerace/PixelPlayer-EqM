// SurroundImagingEngine.kt
package com.theveloper.pixelplay.data.equalizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * One-pole low-pass, used here as the "head shadow" filter: the ear on the far side of a
 * source loses high frequencies because the head diffracts/blocks them more than low
 * frequencies (whose wavelength is large relative to head size). This is a coarse but
 * standard lightweight approximation of that effect.
 */
internal class OnePoleLowPass(private val sampleRate: Float) {
    private var alpha = 1f
    private var state = 0f

    fun set(cutoffHz: Float) {
        val fc = cutoffHz.coerceIn(20f, sampleRate * 0.49f)
        alpha = 1f - exp(-2f * PI.toFloat() * fc / sampleRate)
    }

    fun process(x: Float): Float {
        state += alpha * (x - state)
        return state
    }
}

/**
 * Small circular buffer supporting linear-interpolated fractional-sample delay reads, used to
 * render interaural time difference (ITD).
 */
internal class FractionalDelayLine(private val size: Int) {
    private val buffer = FloatArray(size)
    private var writeIndex = 0

    fun write(sample: Float) {
        buffer[writeIndex] = sample
        writeIndex++
        if (writeIndex >= size) writeIndex = 0
    }

    /** Reads a linearly-interpolated sample `delaySamples` behind the one just written. */
    fun read(delaySamples: Float): Float {
        val clamped = delaySamples.coerceIn(0f, (size - 2).toFloat())
        val floorDelay = clamped.toInt()
        val frac = clamped - floorDelay
        val i0 = floorMod(writeIndex - 1 - floorDelay, size)
        val i1 = floorMod(i0 - 1, size)
        val s0 = buffer[i0]
        val s1 = buffer[i1]
        return s0 + frac * (s1 - s0)
    }

    private fun floorMod(a: Int, b: Int): Int {
        val m = a % b
        return if (m < 0) m + b else m
    }
}

/**
 * Minimal RBJ-cookbook biquad (peaking / high-shelf). Used to approximate the gross
 * front/back spectral cue a real pinna imparts — ITD and ILD alone are symmetric
 * front-to-back (the classic "cone of confusion"), so without this, a source directly
 * ahead and one directly behind render identically. This is still a parametric
 * approximation, not a convolution against a measured HRTF/SOFA dataset, but it's the
 * cue that's actually missing, so it's the highest-value thing to add without that asset.
 */
internal class Biquad(private val sampleRate: Float) {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private var x1 = 0f; private var x2 = 0f
    private var y1 = 0f; private var y2 = 0f

    fun setPeaking(freqHz: Float, gainDb: Float, q: Float) {
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * PI.toFloat() * (freqHz / sampleRate).coerceIn(0.001f, 0.49f)
        val alpha = kotlin.math.sin(w0) / (2f * q)
        val cosw0 = cos(w0)

        val a0n = 1f + alpha / a
        b0 = (1f + alpha * a) / a0n
        b1 = (-2f * cosw0) / a0n
        b2 = (1f - alpha * a) / a0n
        a1 = (-2f * cosw0) / a0n
        a2 = (1f - alpha / a) / a0n
    }

    fun setHighShelf(freqHz: Float, gainDb: Float) {
        val a = 10f.pow(gainDb / 40f)
        val w0 = 2f * PI.toFloat() * (freqHz / sampleRate).coerceIn(0.001f, 0.49f)
        val cosw0 = cos(w0)
        val alpha = kotlin.math.sin(w0) / 2f * sqrt(2f) // shelf slope S = 1
        val twoSqrtAalpha = 2f * sqrt(a) * alpha

        val a0n = (a + 1f) - (a - 1f) * cosw0 + twoSqrtAalpha
        b0 = a * ((a + 1f) + (a - 1f) * cosw0 + twoSqrtAalpha) / a0n
        b1 = (-2f * a * ((a - 1f) + (a + 1f) * cosw0)) / a0n
        b2 = a * ((a + 1f) + (a - 1f) * cosw0 - twoSqrtAalpha) / a0n
        a1 = (2f * ((a - 1f) - (a + 1f) * cosw0)) / a0n
        a2 = ((a + 1f) - (a - 1f) * cosw0 - twoSqrtAalpha) / a0n
    }

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }
}

/**
 * Renders one mono virtual point source (a fixed angle + distance, "calibrated" once) into a
 * stereo contribution using ITD (Woodworth's spherical-head formula), a broadband ILD term, and
 * head-shadow low-pass filtering on the far ear. Distance uses simple inverse-distance
 * attenuation.
 *
 * This is a parametric/simplified approximation of binaural rendering — NOT a measured-HRTF
 * convolution. It has no pinna cues, so it cannot distinguish a source directly in front of you
 * from one directly behind (a known, accepted limitation of ITD/ILD-only panners); true HRTF
 * convolution would need a bundled impulse-response dataset (e.g. a SOFA file) per ear per
 * angle, which is a much larger asset + engineering undertaking than what's here.
 */
internal class BinauralSource(private val sampleRate: Float) {
    var angleRad: Float = 0f
    var distanceM: Float = 1.5f

    private val delayLine = FractionalDelayLine(MAX_DELAY_SAMPLES)
    private val leftShadowFilter = OnePoleLowPass(sampleRate)
    private val rightShadowFilter = OnePoleLowPass(sampleRate)
    private val pinnaFilter = Biquad(sampleRate)

    private var leftDelaySamples = 0f
    private var rightDelaySamples = 0f
    private var leftGain = 1f
    private var rightGain = 1f

    private var outLeft = 0f
    private var outRight = 0f

    /**
     * Recomputes delay/gain/filter targets from the current placement and head yaw. Call once
     * per audio buffer (control rate), not per sample — see [SurroundImagingEngine.updateSpatialParameters].
     */
    fun updateSpatialParameters(headYawRad: Float) {
        val theta = wrap(angleRad - headYawRad)
        val absTheta = abs(theta)
        // Fold front/back onto the same [0, PI/2] range the Woodworth formula is defined for —
        // this is exactly the source of the front/back ambiguity noted above.
        val lateral = if (absTheta <= HALF_PI) absTheta else PI_F - absTheta
        val sign = if (theta >= 0f) 1f else -1f

        val itdSeconds = (HEAD_RADIUS_M / SPEED_OF_SOUND) * (lateral + sin(lateral))
        val itdSamples = itdSeconds * sampleRate

        val distanceGain = REFERENCE_DISTANCE_M / distanceM.coerceAtLeast(MIN_DISTANCE_M)
        val shadowAmount = sin(lateral) // 0 at front/back, 1 at directly lateral
        val ildGain = 1f - 0.3f * shadowAmount
        val shadowCutoff = 1500f + (20000f - 1500f) * (1f - shadowAmount)

        // Frontal sources get a mild concha-resonance lift; rear sources lose it and
        // pick up extra HF shadowing — this is what actually differentiates front/back.
        val isFront = absTheta <= HALF_PI
        if (isFront) {
            pinnaFilter.setPeaking(3200f, 3f, 1.2f)
        } else {
            pinnaFilter.setHighShelf(6000f, -6f)
        }
        
        if (sign >= 0f) {
            // Source to the right: right ear is near (0 extra delay), left ear is far.
            rightDelaySamples = 0f
            leftDelaySamples = itdSamples
            rightGain = distanceGain
            leftGain = distanceGain * ildGain
            leftShadowFilter.set(shadowCutoff)
            rightShadowFilter.set(OPEN_CUTOFF)
        } else {
            leftDelaySamples = 0f
            rightDelaySamples = itdSamples
            leftGain = distanceGain
            rightGain = distanceGain * ildGain
            rightShadowFilter.set(shadowCutoff)
            leftShadowFilter.set(OPEN_CUTOFF)
        }
    }

    /*fun process(mono: Float) {
        delayLine.write(mono)
        val leftRaw = delayLine.read(leftDelaySamples)
        val rightRaw = delayLine.read(rightDelaySamples)
        outLeft = leftShadowFilter.process(leftRaw) * leftGain
        outRight = rightShadowFilter.process(rightRaw) * rightGain
    }*/

    fun process(mono: Float) {
        delayLine.write(pinnaFilter.process(mono))
        val leftRaw = delayLine.read(leftDelaySamples)
        val rightRaw = delayLine.read(rightDelaySamples)
        outLeft = leftShadowFilter.process(leftRaw) * leftGain
        outRight = rightShadowFilter.process(rightRaw) * rightGain
    }

    fun getLeft() = outLeft
    fun getRight() = outRight

    private fun wrap(angle: Float): Float {
        var a = angle
        while (a > PI_F) a -= 2f * PI_F
        while (a < -PI_F) a += 2f * PI_F
        return a
    }

    companion object {
        private const val MAX_DELAY_SAMPLES = 256 // headroom well past max ITD even at 192kHz
        private const val HEAD_RADIUS_M = 0.0875f  // ~average adult head radius
        private const val SPEED_OF_SOUND = 343f    // m/s, room temperature
        private const val REFERENCE_DISTANCE_M = 1.5f
        private const val MIN_DISTANCE_M = 0.15f
        private const val OPEN_CUTOFF = 20000f // effectively bypasses the shadow filter
        private val PI_F = PI.toFloat()
        private val HALF_PI = PI_F / 2f
    }
}

/**
 * Splits each channel into bass / mid / treble (reusing [PolesFilter], the same 4-pole splitter
 * DynamicBassEngine uses), treats each of the resulting 6 band-signals (bass-of-L, bass-of-R,
 * mid-of-L, ...) as its own calibrated virtual point source — modelling a 2-channel setup where
 * each channel's woofer/mid driver/tweeter can sit at a slightly different position, the way a
 * real multi-way speaker's drivers physically do — and binaurally renders all 6 against the
 * listener's current head yaw.
 *
 * Placements are calibrated symmetrically: [angleDegrees] is the right channel's angle (positive
 * = right of the calibrated forward direction), the left channel is mirrored automatically.
 */
class SurroundImagingEngine(private val sampleRate: Float) {
    private val leftSplitter = PolesFilter(sampleRate)
    private val rightSplitter = PolesFilter(sampleRate)

    private val bassLeftSource = BinauralSource(sampleRate)
    private val bassRightSource = BinauralSource(sampleRate)
    private val midLeftSource = BinauralSource(sampleRate)
    private val midRightSource = BinauralSource(sampleRate)
    private val trebleLeftSource = BinauralSource(sampleRate)
    private val trebleRightSource = BinauralSource(sampleRate)

    private var headYawRad = 0f
    private var outSampleL = 0f
    private var outSampleR = 0f

    // Safety limiter: 6 summed virtual sources can exceed the pre-spatialization peak.
    private val ceiling = 0.999f
    private val attackCoeff = exp(-1f / (sampleRate * 0.001f))
    private val releaseCoeff = exp(-1f / (sampleRate * 0.100f))
    private var safetyGainReduction = 1f

    init {
        leftSplitter.set(250f, 4000f)
        rightSplitter.set(250f, 4000f)
        setBassPlacement(15f, 1.5f)
        setMidPlacement(25f, 1.5f)
        setTreblePlacement(30f, 1.5f)
    }

    fun setCrossoverFrequencies(bassMidHz: Float, midTrebleHz: Float) {
        leftSplitter.set(bassMidHz, midTrebleHz)
        rightSplitter.set(bassMidHz, midTrebleHz)
    }

    fun setBassPlacement(angleDegrees: Float, distanceMeters: Float) =
        placeSymmetric(bassLeftSource, bassRightSource, angleDegrees, distanceMeters)

    fun setMidPlacement(angleDegrees: Float, distanceMeters: Float) =
        placeSymmetric(midLeftSource, midRightSource, angleDegrees, distanceMeters)

    fun setTreblePlacement(angleDegrees: Float, distanceMeters: Float) =
        placeSymmetric(trebleLeftSource, trebleRightSource, angleDegrees, distanceMeters)

    private fun placeSymmetric(
        left: BinauralSource, right: BinauralSource, angleDegrees: Float, distanceMeters: Float
    ) {
        val angleRad = angleDegrees * PI.toFloat() / 180f
        left.angleRad = -angleRad
        left.distanceM = distanceMeters
        right.angleRad = angleRad
        right.distanceM = distanceMeters
    }

    /** Current device heading in radians, relative to the calibrated forward direction. */
    fun setHeadYaw(radians: Float) {
        headYawRad = radians
    }

    /**
     * Recomputes ITD/ILD/head-shadow targets for all 6 virtual sources. Deliberately NOT called
     * per-sample: head orientation changes at the sensor's update rate (tens of Hz at most), so
     * recomputing this once per audio buffer is inaudibly coarse while avoiding per-sample
     * trig/exp cost on the audio thread.
     */
    fun updateSpatialParameters() {
        bassLeftSource.updateSpatialParameters(headYawRad)
        bassRightSource.updateSpatialParameters(headYawRad)
        midLeftSource.updateSpatialParameters(headYawRad)
        midRightSource.updateSpatialParameters(headYawRad)
        trebleLeftSource.updateSpatialParameters(headYawRad)
        trebleRightSource.updateSpatialParameters(headYawRad)
    }

    fun processSamples(left: Float, right: Float) {
        leftSplitter.process(left)
        rightSplitter.process(right)

        bassLeftSource.process(leftSplitter.getOut0())    // below crossover 1 (bass)
        bassRightSource.process(rightSplitter.getOut0())
        midLeftSource.process(leftSplitter.getOut2())      // between the two crossovers (mid)
        midRightSource.process(rightSplitter.getOut2())
        trebleLeftSource.process(leftSplitter.getOut1())   // above crossover 2 (treble)
        trebleRightSource.process(rightSplitter.getOut1())

        outSampleL = bassLeftSource.getLeft() + bassRightSource.getLeft() +
            midLeftSource.getLeft() + midRightSource.getLeft() +
            trebleLeftSource.getLeft() + trebleRightSource.getLeft()
        outSampleR = bassLeftSource.getRight() + bassRightSource.getRight() +
            midLeftSource.getRight() + midRightSource.getRight() +
            trebleLeftSource.getRight() + trebleRightSource.getRight()

        val peak = max(abs(outSampleL), abs(outSampleR))
        val targetGain = if (peak > ceiling) ceiling / peak else 1f
        safetyGainReduction = if (targetGain < safetyGainReduction) {
            attackCoeff * safetyGainReduction + (1f - attackCoeff) * targetGain
        } else {
            releaseCoeff * safetyGainReduction + (1f - releaseCoeff) * targetGain
        }
        outSampleL *= safetyGainReduction
        outSampleR *= safetyGainReduction
    }

    fun getLeft() = outSampleL
    fun getRight() = outSampleR
}
