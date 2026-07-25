// DynamicBassEngine.kt
package com.theveloper.pixelplay.data.equalizer

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
}