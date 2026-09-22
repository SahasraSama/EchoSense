package com.echosense.echo.core.audio

import kotlin.math.sqrt

/**
 * High-performance normalized cross-correlator between a reference chirp and a captured signal.
 */
class CrossCorrelator {

    /**
     * Computes the normalized cross-correlation between [reference] (length M)
     * and [signal] (length N, where N >= M).
     *
     * Returns an array of correlation values of length (N - M + 1),
     * where each value is in the range [0.0, 1.0].
     *
     * @param reference The known transmitted chirp (template).
     * @param signal The filtered microphone recording.
     * @return FloatArray of normalized cross-correlation magnitudes.
     */
    fun correlate(reference: FloatArray, signal: FloatArray): FloatArray {
        val m = reference.size
        val n = signal.size
        if (n < m || m == 0) return FloatArray(0)

        val outputLength = n - m + 1
        val result = FloatArray(outputLength)

        // 1. Calculate reference energy
        var refEnergy = 0.0
        for (i in 0 until m) {
            refEnergy += reference[i] * reference[i]
        }
        if (refEnergy < 1e-12) return FloatArray(outputLength)
        val sqrtRefEnergy = sqrt(refEnergy)
        val minEnergyThreshold = 0.005 * refEnergy

        // 2. Precompute sliding window energy for signal
        // windowEnergy[i] = sum(signal[i+k]^2 for k in 0 until m)
        var currentWindowEnergy = 0.0
        for (k in 0 until m) {
            currentWindowEnergy += signal[k] * signal[k]
        }

        // 3. Compute cross-correlation with sliding normalization
        for (i in 0 until outputLength) {
            if (currentWindowEnergy < minEnergyThreshold) {
                result[i] = 0f
            } else {
                var dotProduct = 0.0
                for (k in 0 until m) {
                    dotProduct += reference[k] * signal[i + k]
                }

                val denom = sqrtRefEnergy * sqrt(currentWindowEnergy)
                val normalizedVal = (dotProduct / denom).toFloat()

                // Take absolute value / envelope of correlation
                result[i] = if (normalizedVal < 0f) -normalizedVal else normalizedVal
            }

            // Slide window energy to next sample
            if (i + m < n) {
                val outgoing = signal[i].toDouble()
                val incoming = signal[i + m].toDouble()
                currentWindowEnergy += (incoming * incoming - outgoing * outgoing)
                if (currentWindowEnergy < 0.0) currentWindowEnergy = 0.0
            }
        }

        return result
    }
}
