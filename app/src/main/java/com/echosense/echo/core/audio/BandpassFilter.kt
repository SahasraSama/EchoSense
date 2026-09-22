package com.echosense.echo.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 4th-order cascaded biquad IIR bandpass filter.
 * Isolates transmitted chirp frequencies and suppresses ambient room noise.
 */
class BandpassFilter(
    private val sampleRate: Int = 48000,
    private val lowCutoffHz: Float = 17500f,
    private val highCutoffHz: Float = 21500f
) {
    // Two cascaded biquad sections for steep roll-off
    private val stage1 = Biquad()
    private val stage2 = Biquad()

    init {
        configure(sampleRate, lowCutoffHz, highCutoffHz)
    }

    /**
     * Recomputes filter coefficients for given parameters.
     */
    fun configure(fs: Int, fLow: Float, fHigh: Float) {
        val centerFreq = sqrt((fLow * fHigh).toDouble())
        val bandwidth = (fHigh - fLow).toDouble()
        val q = centerFreq / bandwidth

        // Configure stage 1 & 2 as 2nd-order bandpass with Butterworth Q
        val qStage = q * sqrt(2.0)
        stage1.setBandpass(fs.toDouble(), centerFreq, qStage)
        stage2.setBandpass(fs.toDouble(), centerFreq, qStage)
    }

    /**
     * Resets filter states to zero.
     */
    fun reset() {
        stage1.reset()
        stage2.reset()
    }

    /**
     * Filters a single sample.
     */
    fun process(sample: Float): Float {
        val s1 = stage1.process(sample.toDouble())
        val s2 = stage2.process(s1)
        return s2.toFloat()
    }

    /**
     * Filters an entire FloatArray in-place or returns a new FloatArray.
     */
    fun filter(input: FloatArray, inPlace: Boolean = false): FloatArray {
        val output = if (inPlace) input else FloatArray(input.size)
        reset()
        for (i in input.indices) {
            output[i] = process(input[i])
        }
        return output
    }

    /**
     * Filters a ShortArray (PCM 16-bit) and returns normalized FloatArray.
     */
    fun filterPcm16(input: ShortArray): FloatArray {
        val output = FloatArray(input.size)
        reset()
        for (i in input.indices) {
            val normalized = input[i] / 32768.0f
            output[i] = process(normalized)
        }
        return output
    }

    /**
     * Direct Form II Transposed Biquad implementation.
     */
    private class Biquad {
        private var b0 = 1.0
        private var b1 = 0.0
        private var b2 = 0.0
        private var a1 = 0.0
        private var a2 = 0.0

        private var z1 = 0.0
        private var z2 = 0.0

        fun setBandpass(fs: Double, f0: Double, q: Double) {
            val w0 = 2.0 * PI * f0 / fs
            val alpha = sin(w0) / (2.0 * q)
            val cosW0 = cos(w0)

            // Constant 0 dB peak gain bandpass
            val a0 = 1.0 + alpha
            b0 = alpha / a0
            b1 = 0.0
            b2 = -alpha / a0
            a1 = (-2.0 * cosW0) / a0
            a2 = (1.0 - alpha) / a0

            reset()
        }

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }

        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }
}
