package com.echosense.echo.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthesizes linear frequency-modulated (FMCW) chirps with Tukey/Hann windowing
 * to suppress clicks and spectral leakage in near-ultrasound bands.
 */
class ChirpGenerator(
    private val config: AudioConfig = AudioConfig()
) {

    /**
     * Generates a normalized FloatArray (-1.0f to 1.0f) representing the linear chirp.
     *
     * s(t) = w(t) * sin(2 * pi * (f0 * t + ((f1 - f0) / (2 * T)) * t^2))
     *
     * @param f0 Starting frequency in Hz.
     * @param f1 Ending frequency in Hz.
     * @param durationMs Duration of the chirp in milliseconds.
     * @param sampleRate Sampling rate in Hz.
     * @param taperRatio Tukey window taper ratio (0.0 = rectangular, 1.0 = Hann).
     */
    fun generateChirp(
        f0: Float = config.startFreqHz,
        f1: Float = config.endFreqHz,
        durationMs: Float = config.chirpDurationMs,
        sampleRate: Int = config.sampleRate,
        taperRatio: Float = 0.25f
    ): FloatArray {
        val nSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val buffer = FloatArray(nSamples)
        val durationSec = durationMs / 1000f
        val chirpRate = (f1 - f0) / (2.0 * durationSec)

        for (i in 0 until nSamples) {
            val t = i.toDouble() / sampleRate
            val phase = 2.0 * PI * (f0 * t + chirpRate * t * t)
            val window = tukeyWindow(i, nSamples, taperRatio)
            buffer[i] = (window * sin(phase)).toFloat()
        }
        return buffer
    }

    /**
     * Generates a 16-bit PCM ShortArray from the float chirp.
     */
    fun generatePcm16Chirp(
        f0: Float = config.startFreqHz,
        f1: Float = config.endFreqHz,
        durationMs: Float = config.chirpDurationMs,
        sampleRate: Int = config.sampleRate,
        amplitude: Float = 0.95f
    ): ShortArray {
        val floatChirp = generateChirp(f0, f1, durationMs, sampleRate)
        val shortBuffer = ShortArray(floatChirp.size)
        val scale = (Short.MAX_VALUE * amplitude).coerceIn(0f, Short.MAX_VALUE.toFloat())

        for (i in floatChirp.indices) {
            shortBuffer[i] = (floatChirp[i] * scale).toInt().toShort()
        }
        return shortBuffer
    }

    /**
     * Generates a 16-bit little-endian ByteArray for AudioTrack.
     */
    fun generatePcm16ByteArray(
        f0: Float = config.startFreqHz,
        f1: Float = config.endFreqHz,
        durationMs: Float = config.chirpDurationMs,
        sampleRate: Int = config.sampleRate,
        amplitude: Float = 0.95f
    ): ByteArray {
        val shortArray = generatePcm16Chirp(f0, f1, durationMs, sampleRate, amplitude)
        val byteArray = ByteArray(shortArray.size * 2)
        for (i in shortArray.indices) {
            val v = shortArray[i].toInt()
            byteArray[i * 2] = (v and 0xFF).toByte()
            byteArray[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return byteArray
    }

    /**
     * Tukey window (tapered cosine window) function.
     * Smoothly ramps up and down to prevent spectral splatter.
     */
    private fun tukeyWindow(index: Int, totalSamples: Int, alpha: Float): Double {
        if (alpha <= 0.0) return 1.0
        if (alpha >= 1.0) {
            // Hann window
            return 0.5 * (1.0 - cos(2.0 * PI * index / (totalSamples - 1)))
        }

        val point = index.toDouble() / (totalSamples - 1)
        val alphaHalf = alpha / 2.0

        return when {
            point < alphaHalf -> {
                0.5 * (1.0 + cos(PI * (2.0 * point / alpha - 1.0)))
            }
            point <= 1.0 - alphaHalf -> {
                1.0
            }
            else -> {
                0.5 * (1.0 + cos(PI * (2.0 * point / alpha - 2.0 / alpha + 1.0)))
            }
        }
    }
}
