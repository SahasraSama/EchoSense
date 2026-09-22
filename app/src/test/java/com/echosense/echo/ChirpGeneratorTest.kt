package com.echosense.echo

import com.echosense.echo.core.audio.AudioConfig
import com.echosense.echo.core.audio.ChirpGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChirpGeneratorTest {

    @Test
    fun testChirpGenerationDimensionsAndAmplitude() {
        val config = AudioConfig(sampleRate = 48000, chirpDurationMs = 6.0f)
        val generator = ChirpGenerator(config)

        val chirp = generator.generateChirp(
            f0 = 18000f,
            f1 = 21000f,
            durationMs = 6.0f,
            sampleRate = 48000,
            taperRatio = 0.25f
        )

        // 6ms at 48kHz = 288 samples
        val expectedSamples = (48000 * 0.006f).toInt()
        assertEquals(expectedSamples, chirp.size)

        // Verify windowing: first and last samples should be tapered near 0
        assertTrue("First sample should be near zero", abs(chirp.first()) < 0.1f)
        assertTrue("Last sample should be near zero", abs(chirp.last()) < 0.1f)

        // Verify maximum amplitude is normalized within [-1.0, 1.0]
        var maxAmp = 0f
        for (v in chirp) {
            val a = abs(v)
            if (a > maxAmp) maxAmp = a
            assertTrue("Sample out of bounds: $v", a <= 1.001f)
        }
        assertTrue("Peak amplitude should be significant", maxAmp > 0.8f)
    }

    @Test
    fun testPcm16Conversion() {
        val config = AudioConfig(sampleRate = 48000, chirpDurationMs = 5.0f)
        val generator = ChirpGenerator(config)

        val pcm = generator.generatePcm16Chirp(
            f0 = 18000f,
            f1 = 21000f,
            durationMs = 5.0f,
            sampleRate = 48000,
            amplitude = 0.95f
        )

        val bytes = generator.generatePcm16ByteArray(
            f0 = 18000f,
            f1 = 21000f,
            durationMs = 5.0f,
            sampleRate = 48000,
            amplitude = 0.95f
        )

        assertEquals(pcm.size * 2, bytes.size)
        // Verify little-endian conversion of first sample
        val reconstructedFirst = (bytes[0].toInt() and 0xFF) or (bytes[1].toInt() shl 8)
        assertEquals(pcm[0].toInt(), reconstructedFirst.toShort().toInt())
    }
}
