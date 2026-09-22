package com.echosense.echo

import com.echosense.echo.core.audio.AudioConfig
import com.echosense.echo.core.audio.DistanceEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DistanceEstimatorTest {

    private val config = AudioConfig(defaultTemperatureCelsius = 20.0f)
    private val estimator = DistanceEstimator(config, smoothingAlpha = 0.5f)

    @Test
    fun testDistanceConversionAt20Celsius() {
        val speed = config.speedOfSound(20.0f) // ~343.42 m/s
        val targetDistance = 1.0f // 1 meter
        val roundTripTimeMs = (2.0f * targetDistance / speed) * 1000f

        val result = estimator.estimateDistance(
            roundTripDelayMs = roundTripTimeMs,
            isEchoValid = true,
            temperatureCelsius = 20.0f,
            confidenceScore = 0.9f
        )

        assertTrue(result.isReliable)
        assertTrue("Distance should be within 1cm of 1.0m", abs(result.smoothedDistanceMeters - 1.0f) < 0.01f)
        assertEquals("1.00 m", result.formattedDisplay)
    }

    @Test
    fun testTemperatureCompensation() {
        val delayMs = 5.82f // ~1 meter at 20°C

        val resAtZero = estimator.estimateDistance(
            roundTripDelayMs = delayMs,
            isEchoValid = true,
            temperatureCelsius = 0.0f,
            confidenceScore = 0.9f
        )

        estimator.reset()
        val resAt35 = estimator.estimateDistance(
            roundTripDelayMs = delayMs,
            isEchoValid = true,
            temperatureCelsius = 35.0f,
            confidenceScore = 0.9f
        )

        // Sound travels faster in warmer air, so for the same time delay, distance is larger at 35°C than 0°C
        assertTrue(
            "Distance at 35°C (${resAt35.smoothedDistanceMeters}m) should be greater than at 0°C (${resAtZero.smoothedDistanceMeters}m)",
            resAt35.smoothedDistanceMeters > resAtZero.smoothedDistanceMeters
        )
    }

    @Test
    fun testOutlierRejection() {
        estimator.reset()
        // Baseline 1.0m
        val speed = config.speedOfSound(20.0f)
        val baselineDelay = (2.0f * 1.0f / speed) * 1000f

        estimator.estimateDistance(baselineDelay, true, 20f, 0.9f)

        // Sudden transient spike to 2.5m (1.5m jump)
        val spikeDelay = (2.0f * 2.5f / speed) * 1000f
        val spikeResult = estimator.estimateDistance(spikeDelay, true, 20f, 0.9f)

        // First outlier should be rejected, preserving previous smoothed value
        assertTrue("Transient outlier should be rejected", abs(spikeResult.smoothedDistanceMeters - 1.0f) < 0.05f)
    }

    @Test
    fun testHonestFormattingForMediumConfidence() {
        estimator.reset()
        val speed = config.speedOfSound(20.0f)
        val delayMs = (2.0f * 0.82f / speed) * 1000f

        val result = estimator.estimateDistance(
            roundTripDelayMs = delayMs,
            isEchoValid = true,
            temperatureCelsius = 20f,
            confidenceScore = 0.55f // Medium confidence
        )

        assertEquals("~0.8 m", result.formattedDisplay)
    }

    @Test
    fun testInvalidEchoYieldsNoReliableEcho() {
        estimator.reset()
        val result = estimator.estimateDistance(
            roundTripDelayMs = 0f,
            isEchoValid = false,
            confidenceScore = 0f
        )

        assertFalse(result.isReliable)
        assertEquals("NO RELIABLE ECHO", result.formattedDisplay)
    }
}
