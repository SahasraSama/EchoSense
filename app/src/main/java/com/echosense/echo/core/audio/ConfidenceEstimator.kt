package com.echosense.echo.core.audio

import kotlin.math.abs

enum class ConfidenceRating {
    HIGH,
    MEDIUM,
    LOW,
    UNRELIABLE
}

data class ConfidenceResult(
    val score: Float, // 0.0 to 1.0
    val rating: ConfidenceRating,
    val explanation: String
)

/**
 * Computes an objective confidence score for acoustic measurements based on
 * correlation peak height, SNR, PSR, temporal stability, and ambient noise.
 */
class ConfidenceEstimator(
    private val config: AudioConfig = AudioConfig()
) {
    private val history = ArrayDeque<Float>(5)

    fun reset() {
        history.clear()
    }

    /**
     * Calculates confidence from echo metrics and temporal history.
     */
    fun estimateConfidence(
        peakCorrelation: Float,
        snrDb: Float,
        psr: Float,
        distanceMeters: Float,
        ambientNoiseRms: Float
    ): ConfidenceResult {
        if (distanceMeters <= 0f || peakCorrelation <= 0f) {
            return ConfidenceResult(
                score = 0f,
                rating = ConfidenceRating.UNRELIABLE,
                explanation = "No echo detected"
            )
        }

        // 1. Correlation factor (0.0 to 1.0)
        val corrScore = (peakCorrelation / 0.8f).coerceIn(0f, 1f)

        // 2. SNR factor: 6 dB min, 18 dB ideal
        val snrScore = ((snrDb - config.snrThresholdDb) / (18f - config.snrThresholdDb)).coerceIn(0f, 1f)

        // 3. PSR factor: 1.6 min, 4.0 ideal
        val psrScore = ((psr - config.psrThreshold) / (4.0f - config.psrThreshold)).coerceIn(0f, 1f)

        // 4. Temporal consistency check
        var stabilityScore = 0.8f
        if (history.isNotEmpty()) {
            var diffSum = 0f
            for (prev in history) {
                diffSum += abs(distanceMeters - prev)
            }
            val avgDiff = diffSum / history.size
            stabilityScore = when {
                avgDiff < 0.08f -> 1.0f // within 8cm
                avgDiff < 0.20f -> 0.75f
                avgDiff < 0.40f -> 0.50f
                else -> 0.25f
            }
        }

        // Push to history
        if (history.size >= 5) history.removeFirst()
        history.addLast(distanceMeters)

        // 5. Environmental noise penalty
        val noisePenalty = if (ambientNoiseRms > 0.08f) 0.25f else 0.0f

        // Combined weighted score
        val rawScore = (0.35f * corrScore + 0.30f * snrScore + 0.20f * psrScore + 0.15f * stabilityScore) - noisePenalty
        val finalScore = rawScore.coerceIn(0f, 1f)

        val rating = when {
            finalScore >= 0.75f -> ConfidenceRating.HIGH
            finalScore >= 0.45f -> ConfidenceRating.MEDIUM
            finalScore >= 0.20f -> ConfidenceRating.LOW
            else -> ConfidenceRating.UNRELIABLE
        }

        val explanation = when (rating) {
            ConfidenceRating.HIGH -> "Strong peak, clean acoustic reflection"
            ConfidenceRating.MEDIUM -> "Acceptable reflection, moderate SNR"
            ConfidenceRating.LOW -> "Weak reflection or elevated noise"
            ConfidenceRating.UNRELIABLE -> "Acoustic signal uncertain"
        }

        return ConfidenceResult(
            score = finalScore,
            rating = rating,
            explanation = explanation
        )
    }
}
