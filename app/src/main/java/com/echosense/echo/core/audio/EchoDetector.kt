package com.echosense.echo.core.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Result of echo detection from cross-correlation analysis.
 */
data class EchoDetectionResult(
    val directPathSample: Int,
    val echoSample: Int,
    val directPathDelayMs: Float,
    val roundTripDelayMs: Float,
    val peakCorrelation: Float,
    val snrDb: Float,
    val psr: Float,
    val isValidEcho: Boolean,
    val statusMessage: String
)

/**
 * Detects direct-path and reflected echo peaks from normalized cross-correlation output.
 * Distinguishes direct acoustic leakage from true obstacle reflections using local PSR analysis.
 */
class EchoDetector(
    private val config: AudioConfig = AudioConfig()
) {

    /**
     * Analyzes cross-correlation data to detect the direct-path reference peak
     * and subsequent obstacle reflection peaks.
     *
     * @param correlation Normalized cross-correlation output.
     * @param sampleRate Audio sampling rate in Hz.
     * @param calibratedDirectDelayMs Optional calibrated direct-path delay in ms.
     * @return [EchoDetectionResult] detailing the detection outcome.
     */
    fun detectEcho(
        correlation: FloatArray,
        sampleRate: Int = config.sampleRate,
        calibratedDirectDelayMs: Float? = null
    ): EchoDetectionResult {
        if (correlation.isEmpty()) {
            return EchoDetectionResult(
                directPathSample = -1,
                echoSample = -1,
                directPathDelayMs = 0f,
                roundTripDelayMs = 0f,
                peakCorrelation = 0f,
                snrDb = 0f,
                psr = 0f,
                isValidEcho = false,
                statusMessage = "NO_DATA"
            )
        }

        // 1. Detect Direct-Path Peak
        // Direct-path travels 5-15cm (0.15ms - 0.5ms) plus hardware buffer delay (typically 0-5ms).
        // Coherent direct path coupling produces high correlation magnitude (>= 0.45).
        val directSearchWindowSamples = (sampleRate * 0.012f).toInt().coerceAtMost(correlation.size)
        var directPeakSample = -1
        var directPeakValue = 0f

        if (calibratedDirectDelayMs != null && calibratedDirectDelayMs > 0f) {
            val centerIdx = (calibratedDirectDelayMs * sampleRate / 1000f).toInt().coerceIn(0, correlation.size - 1)
            val searchRadius = (sampleRate * 0.002f).toInt()
            val startIdx = (centerIdx - searchRadius).coerceAtLeast(0)
            val endIdx = (centerIdx + searchRadius).coerceAtMost(correlation.size - 1)

            for (i in startIdx..endIdx) {
                if (correlation[i] > directPeakValue && correlation[i] >= 0.25f) {
                    directPeakValue = correlation[i]
                    directPeakSample = i
                }
            }
        } else {
            // Find strongest peak in early window (must be coherent direct path chirp, >= 0.45)
            for (i in 1 until directSearchWindowSamples - 1) {
                val v = correlation[i]
                if (v > directPeakValue && v > correlation[i - 1] && v > correlation[i + 1] && v >= 0.45f) {
                    directPeakValue = v
                    directPeakSample = i
                }
            }
        }

        val directDelayMs = if (directPeakSample >= 0) {
            (directPeakSample.toFloat() / sampleRate) * 1000f
        } else {
            0f
        }

        // 2. Define Reflection Search Window
        // Blanking window covers acoustic speaker-to-mic chirp emission and decay duration
        val blankingSamples = ((config.chirpDurationMs + config.directPathBlankingMs) * sampleRate / 1000f).toInt()
        val minEchoSample = if (directPeakSample >= 0) directPeakSample + blankingSamples else blankingSamples
        val maxDelayMs = (config.maxRoundTripDelaySec() * 1000f)
        val maxEchoSample = (minEchoSample + (maxDelayMs * sampleRate / 1000f).toInt()).coerceAtMost(correlation.size - 1)

        if (minEchoSample >= maxEchoSample) {
            return EchoDetectionResult(
                directPathSample = directPeakSample,
                echoSample = -1,
                directPathDelayMs = directDelayMs,
                roundTripDelayMs = 0f,
                peakCorrelation = 0f,
                snrDb = 0f,
                psr = 0f,
                isValidEcho = false,
                statusMessage = "NO_RELIABLE_ECHO"
            )
        }

        // 3. Estimate Global Noise Floor in Search Window
        var noiseSum = 0.0
        var noiseSqSum = 0.0
        var count = 0
        for (i in minEchoSample..maxEchoSample) {
            val v = correlation[i].toDouble()
            noiseSum += v
            noiseSqSum += v * v
            count++
        }
        val noiseRms = sqrt(if (count > 0) noiseSqSum / count else 0.0)

        // 4. Detect Candidate Reflection Peaks in Search Window
        var bestEchoSample = -1
        var bestEchoPeak = 0f

        for (i in (minEchoSample + 1) until maxEchoSample) {
            val v = correlation[i]
            // Local maximum check
            if (v > correlation[i - 1] && v > correlation[i + 1]) {
                if (v > bestEchoPeak && v >= config.correlationThreshold) {
                    bestEchoPeak = v
                    bestEchoSample = i
                }
            }
        }

        if (bestEchoSample < 0) {
            return EchoDetectionResult(
                directPathSample = directPeakSample,
                echoSample = -1,
                directPathDelayMs = directDelayMs,
                roundTripDelayMs = 0f,
                peakCorrelation = bestEchoPeak,
                snrDb = 0f,
                psr = 0f,
                isValidEcho = false,
                statusMessage = "NO_RELIABLE_ECHO"
            )
        }

        // 5. Compute Local Peak-to-Sidelobe Ratio (PSR) around candidate echo peak
        val localRadius = (sampleRate * 0.003f).toInt().coerceAtLeast(15) // ~3ms neighborhood
        val localStart = (bestEchoSample - localRadius).coerceAtLeast(minEchoSample)
        val localEnd = (bestEchoSample + localRadius).coerceAtMost(maxEchoSample)

        var localSum = 0.0
        var localSqSum = 0.0
        var localCount = 0
        for (k in localStart..localEnd) {
            if (abs(k - bestEchoSample) > 3) {
                val v = correlation[k].toDouble()
                localSum += v
                localSqSum += v * v
                localCount++
            }
        }

        val localMean = if (localCount > 0) localSum / localCount else 0.0
        val localVar = if (localCount > 1) max(0.0, (localSqSum - (localSum * localSum) / localCount) / (localCount - 1)) else 0.0
        val localStdDev = sqrt(localVar)

        val psr = if (localStdDev > 1e-5) {
            ((bestEchoPeak - localMean) / localStdDev).toFloat()
        } else {
            0f
        }

        val snrDb = if (noiseRms > 1e-6) {
            (20.0 * log10(bestEchoPeak.toDouble() / noiseRms)).toFloat()
        } else {
            0f
        }

        val roundTripSamples = bestEchoSample - (if (directPeakSample >= 0) directPeakSample else 0)
        val roundTripDelayMs = (roundTripSamples.toFloat() / sampleRate) * 1000f

        // 6. Echo Candidate Validation
        val hasDirectPath = directPeakSample >= 0
        val isValid = hasDirectPath &&
                bestEchoPeak >= config.correlationThreshold &&
                snrDb >= config.snrThresholdDb &&
                psr >= config.psrThreshold

        val status = if (isValid) "VALID_ECHO" else "NO_RELIABLE_ECHO"

        return EchoDetectionResult(
            directPathSample = directPeakSample,
            echoSample = bestEchoSample,
            directPathDelayMs = directDelayMs,
            roundTripDelayMs = roundTripDelayMs,
            peakCorrelation = bestEchoPeak,
            snrDb = snrDb,
            psr = psr,
            isValidEcho = isValid,
            statusMessage = status
        )
    }
}
