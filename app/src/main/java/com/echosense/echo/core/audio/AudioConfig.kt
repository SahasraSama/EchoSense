package com.echosense.echo.core.audio

/**
 * Configuration parameters for real acoustic echo ranging on Android devices.
 */
data class AudioConfig(
    val sampleRate: Int = 48000,
    val chirpDurationMs: Float = 2.0f,
    val startFreqHz: Float = 18000f,
    val endFreqHz: Float = 21000f,
    val fallbackStartFreqHz: Float = 15000f,
    val fallbackEndFreqHz: Float = 18000f,
    val speedOfSoundBase: Float = 331.3f, // m/s at 0°C
    val defaultTemperatureCelsius: Float = 20.0f,
    val minDistanceMeters: Float = 0.25f,
    val maxDistanceMeters: Float = 3.5f,
    val correlationThreshold: Float = 0.30f,
    val snrThresholdDb: Float = 6.0f,
    val psrThreshold: Float = 1.6f,
    val directPathBlankingMs: Float = 0.4f // Blanking window after direct sound
) {
    /**
     * Speed of sound calculated with temperature compensation:
     * c(T) = 331.3 * sqrt(1 + T / 273.15)
     */
    fun speedOfSound(temperatureCelsius: Float = defaultTemperatureCelsius): Float {
        return (speedOfSoundBase * Math.sqrt(1.0 + temperatureCelsius / 273.15)).toFloat()
    }

    /**
     * Number of audio samples for the chirp.
     */
    val chirpSampleCount: Int
        get() = (sampleRate * (chirpDurationMs / 1000f)).toInt()

    /**
     * Minimum round-trip delay in seconds corresponding to [minDistanceMeters].
     */
    fun minRoundTripDelaySec(temperatureCelsius: Float = defaultTemperatureCelsius): Float {
        return (2.0f * minDistanceMeters) / speedOfSound(temperatureCelsius)
    }

    /**
     * Maximum round-trip delay in seconds corresponding to [maxDistanceMeters].
     */
    fun maxRoundTripDelaySec(temperatureCelsius: Float = defaultTemperatureCelsius): Float {
        return (2.0f * maxDistanceMeters) / speedOfSound(temperatureCelsius)
    }
}
