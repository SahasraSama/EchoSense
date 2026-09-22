package com.echosense.echo.core.audio

import java.util.Locale
import kotlin.math.abs

/**
 * Result of distance estimation with smoothed and raw values.
 */
data class DistanceResult(
    val rawDistanceMeters: Float,
    val smoothedDistanceMeters: Float,
    val roundTripTimeMs: Float,
    val formattedDisplay: String,
    val isReliable: Boolean
)

/**
 * Converts acoustic round-trip time-of-flight to metric distance,
 * with temperature compensation and exponential smoothing.
 */
class DistanceEstimator(
    private val config: AudioConfig = AudioConfig(),
    private val smoothingAlpha: Float = 0.35f,
    private val outlierRejectionThresholdMeters: Float = 0.8f
) {
    private var smoothedDistance: Float? = null
    private var consecutiveOutlierCount: Int = 0

    /**
     * Resets smoothing history.
     */
    fun reset() {
        smoothedDistance = null
        consecutiveOutlierCount = 0
    }

    /**
     * Estimates distance from round-trip delay.
     *
     * @param roundTripDelayMs Round-trip time in milliseconds.
     * @param isEchoValid Whether the echo detector flagged this as a valid reflection.
     * @param temperatureCelsius Current ambient temperature in Celsius (default: 20°C).
     * @param confidenceScore 0.0 to 1.0 confidence.
     * @return [DistanceResult]
     */
    fun estimateDistance(
        roundTripDelayMs: Float,
        isEchoValid: Boolean,
        temperatureCelsius: Float = config.defaultTemperatureCelsius,
        confidenceScore: Float = 1.0f
    ): DistanceResult {
        if (!isEchoValid || roundTripDelayMs <= 0f) {
            return DistanceResult(
                rawDistanceMeters = -1f,
                smoothedDistanceMeters = -1f,
                roundTripTimeMs = roundTripDelayMs,
                formattedDisplay = "NO RELIABLE ECHO",
                isReliable = false
            )
        }

        val c = config.speedOfSound(temperatureCelsius)
        val roundTripSec = roundTripDelayMs / 1000f
        val rawDistance = (c * roundTripSec) / 2.0f

        // Outlier rejection and smoothing
        val currentSmoothed = smoothedDistance
        val finalDistance: Float

        if (currentSmoothed == null) {
            smoothedDistance = rawDistance
            finalDistance = rawDistance
            consecutiveOutlierCount = 0
        } else {
            val diff = abs(rawDistance - currentSmoothed)
            if (diff > outlierRejectionThresholdMeters && consecutiveOutlierCount < 3) {
                // Reject transient outlier
                consecutiveOutlierCount++
                finalDistance = currentSmoothed
            } else {
                // Valid update
                consecutiveOutlierCount = 0
                val updated = currentSmoothed + smoothingAlpha * (rawDistance - currentSmoothed)
                smoothedDistance = updated
                finalDistance = updated
            }
        }

        // Realistic range clamping: only claim 0.25m to 3.5m
        if (finalDistance < config.minDistanceMeters || finalDistance > config.maxDistanceMeters) {
            return DistanceResult(
                rawDistanceMeters = rawDistance,
                smoothedDistanceMeters = finalDistance,
                roundTripTimeMs = roundTripDelayMs,
                formattedDisplay = "OUT OF RANGE",
                isReliable = false
            )
        }

        // Format based on confidence honesty
        val display = when {
            confidenceScore >= 0.75f -> String.format(Locale.US, "%.2f m", finalDistance)
            confidenceScore >= 0.40f -> String.format(Locale.US, "~%.1f m", finalDistance)
            else -> "NO RELIABLE ECHO"
        }

        return DistanceResult(
            rawDistanceMeters = rawDistance,
            smoothedDistanceMeters = finalDistance,
            roundTripTimeMs = roundTripDelayMs,
            formattedDisplay = display,
            isReliable = confidenceScore >= 0.40f
        )
    }
}
