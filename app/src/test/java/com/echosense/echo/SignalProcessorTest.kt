package com.echosense.echo

import com.echosense.echo.core.audio.AudioConfig
import com.echosense.echo.core.audio.BandpassFilter
import com.echosense.echo.core.audio.ChirpGenerator
import com.echosense.echo.core.audio.CrossCorrelator
import com.echosense.echo.core.audio.EchoDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SignalProcessorTest {

    private val sampleRate = 48000
    private val config = AudioConfig(
        sampleRate = sampleRate,
        chirpDurationMs = 2.0f,
        startFreqHz = 18000f,
        endFreqHz = 21000f,
        correlationThreshold = 0.32f,
        directPathBlankingMs = 0.4f
    )

    private val generator = ChirpGenerator(config)
    private val filter = BandpassFilter(sampleRate, 17500f, 21500f)
    private val correlator = CrossCorrelator()
    private val detector = EchoDetector(config)

    /**
     * Helper to create a synthetic recording with a direct-path pulse and a reflected echo.
     */
    private fun createSyntheticSignal(
        directDelayMs: Float,
        echoDelayMs: Float,
        directAttenuation: Float = 0.9f,
        echoAttenuation: Float = 0.45f,
        noiseLevel: Float = 0.02f
    ): FloatArray {
        val totalDurationMs = 40.0f
        val totalSamples = (sampleRate * (totalDurationMs / 1000f)).toInt()
        val signal = FloatArray(totalSamples)

        val chirp = generator.generateChirp(
            f0 = config.startFreqHz,
            f1 = config.endFreqHz,
            durationMs = config.chirpDurationMs,
            sampleRate = sampleRate
        )

        // Add random background noise
        val rng = Random(42)
        for (i in signal.indices) {
            signal[i] = (rng.nextFloat() * 2f - 1f) * noiseLevel
        }

        // Add Direct Path
        val directSampleOffset = (directDelayMs * sampleRate / 1000f).toInt()
        for (i in chirp.indices) {
            val idx = directSampleOffset + i
            if (idx < totalSamples) {
                signal[idx] += chirp[i] * directAttenuation
            }
        }

        // Add Reflected Echo
        val echoSampleOffset = ((directDelayMs + echoDelayMs) * sampleRate / 1000f).toInt()
        for (i in chirp.indices) {
            val idx = echoSampleOffset + i
            if (idx < totalSamples) {
                signal[idx] += chirp[i] * echoAttenuation
            }
        }

        return signal
    }

    @Test
    fun testReflectionPeakDetectionAtDifferentDistances() {
        val referenceChirp = generator.generateChirp(
            f0 = config.startFreqHz,
            f1 = config.endFreqHz,
            durationMs = config.chirpDurationMs,
            sampleRate = sampleRate
        )

        val speedOfSound = config.speedOfSound(20.0f) // ~343.4 m/s
        val testDistancesMeters = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f)
        val directDelayMs = 0.3f // 0.3 ms physical phone speaker-to-mic direct path

        for (distance in testDistancesMeters) {
            // Round trip delay: deltaT = 2 * d / c
            val expectedEchoDelayMs = (2f * distance / speedOfSound) * 1000f

            val syntheticSignal = createSyntheticSignal(
                directDelayMs = directDelayMs,
                echoDelayMs = expectedEchoDelayMs,
                directAttenuation = 0.9f,
                echoAttenuation = 0.5f,
                noiseLevel = 0.01f
            )

            // Bandpass filter
            val filtered = filter.filter(syntheticSignal)

            // Cross-correlate
            val correlation = correlator.correlate(referenceChirp, filtered)

            // Detect echo
            val result = detector.detectEcho(
                correlation = correlation,
                sampleRate = sampleRate,
                calibratedDirectDelayMs = directDelayMs
            )

            assertTrue("Echo at $distance m should be valid", result.isValidEcho)
            assertEquals("VALID_ECHO", result.statusMessage)

            // Delay error should be under 0.25 ms (less than ~4 cm error)
            val delayError = abs(result.roundTripDelayMs - expectedEchoDelayMs)
            assertTrue("Delay error ($delayError ms) at $distance m should be < 0.25 ms", delayError < 0.25f)

            // Verify peak correlation is strong
            assertTrue("Peak correlation should be >= threshold", result.peakCorrelation >= config.correlationThreshold)
            assertTrue("SNR should be >= threshold", result.snrDb >= config.snrThresholdDb)
        }
    }

    @Test
    fun testDirectPathBlankingPreventsSelfDetection() {
        val referenceChirp = generator.generateChirp(
            f0 = config.startFreqHz,
            f1 = config.endFreqHz,
            durationMs = config.chirpDurationMs,
            sampleRate = sampleRate
        )

        // Signal with ONLY direct-path, NO obstacle reflection
        val syntheticSignal = createSyntheticSignal(
            directDelayMs = 0.3f,
            echoDelayMs = 0f, // No echo
            directAttenuation = 0.9f,
            echoAttenuation = 0.0f,
            noiseLevel = 0.01f
        )

        val filtered = filter.filter(syntheticSignal)
        val correlation = correlator.correlate(referenceChirp, filtered)

        val result = detector.detectEcho(
            correlation = correlation,
            sampleRate = sampleRate,
            calibratedDirectDelayMs = 0.3f
        )

        // Should NOT detect direct-path as an obstacle echo
        assertFalse("Direct path sound must not be flagged as a valid reflection", result.isValidEcho)
        assertEquals("NO_RELIABLE_ECHO", result.statusMessage)
    }

    @Test
    fun testPureNoiseReportsNoReliableEcho() {
        val referenceChirp = generator.generateChirp(
            f0 = config.startFreqHz,
            f1 = config.endFreqHz,
            durationMs = config.chirpDurationMs,
            sampleRate = sampleRate
        )

        // Pure random noise without any chirp
        val noiseSignal = FloatArray(sampleRate / 20) // 50ms
        val rng = Random(123)
        for (i in noiseSignal.indices) {
            noiseSignal[i] = (rng.nextFloat() * 2f - 1f) * 0.1f
        }

        val filtered = filter.filter(noiseSignal)
        val correlation = correlator.correlate(referenceChirp, filtered)

        val result = detector.detectEcho(
            correlation = correlation,
            sampleRate = sampleRate
        )

        assertFalse("Pure noise must report no reliable echo", result.isValidEcho)
    }
}
