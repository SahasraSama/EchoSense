package com.echosense.echo

import com.echosense.echo.core.audio.AudioConfig
import com.echosense.echo.core.audio.ConfidenceEstimator
import com.echosense.echo.core.audio.ConfidenceRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceEstimatorTest {

    private val config = AudioConfig()
    private val estimator = ConfidenceEstimator(config)

    @Test
    fun testHighConfidenceCalculation() {
        estimator.reset()
        val result = estimator.estimateConfidence(
            peakCorrelation = 0.75f,
            snrDb = 16.0f,
            psr = 3.5f,
            distanceMeters = 1.0f,
            ambientNoiseRms = 0.01f
        )

        assertEquals(ConfidenceRating.HIGH, result.rating)
        assertTrue("High confidence score should be >= 0.75", result.score >= 0.75f)
    }

    @Test
    fun testUnreliableConfidenceWithHighNoise() {
        estimator.reset()
        val result = estimator.estimateConfidence(
            peakCorrelation = 0.30f,
            snrDb = 6.5f,
            psr = 1.7f,
            distanceMeters = 1.0f,
            ambientNoiseRms = 0.12f // Very noisy room
        )

        assertTrue(
            "High environmental noise should degrade confidence",
            result.rating == ConfidenceRating.LOW || result.rating == ConfidenceRating.UNRELIABLE
        )
    }

    @Test
    fun testZeroDistanceYieldsUnreliable() {
        estimator.reset()
        val result = estimator.estimateConfidence(
            peakCorrelation = 0f,
            snrDb = 0f,
            psr = 0f,
            distanceMeters = 0f,
            ambientNoiseRms = 0.01f
        )

        assertEquals(ConfidenceRating.UNRELIABLE, result.rating)
        assertEquals(0f, result.score, 0.001f)
    }
}
