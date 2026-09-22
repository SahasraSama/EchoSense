package com.echosense.echo

import com.echosense.echo.core.fusion.SensingMode
import com.echosense.echo.core.fusion.SensorFusionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorFusionEngineTest {

    @Test
    fun testModeTransitionsBasedOnAmbientLight() {
        val engine = SensorFusionEngine()

        // 1. Bright light (120 lux) -> VISION mode
        val brightState = engine.fuse(
            ambientLux = 120f,
            cameraObstacle = "Chair",
            cameraConfidence = 0.85f,
            visualDistanceMeters = 1.2f,
            acousticDistanceMeters = -1f,
            acousticConfidenceScore = 0f,
            isAcousticReliable = false
        )
        assertEquals(SensingMode.VISION, brightState.activeMode)
        assertEquals("Chair", brightState.detectedLabel)

        // 2. Dim light (25 lux) -> HYBRID mode
        val dimState = engine.fuse(
            ambientLux = 25f,
            cameraObstacle = "Chair",
            cameraConfidence = 0.50f,
            visualDistanceMeters = 1.2f,
            acousticDistanceMeters = 0.95f,
            acousticConfidenceScore = 0.88f,
            isAcousticReliable = true
        )
        assertEquals(SensingMode.HYBRID, dimState.activeMode)
        // Hybrid mode anchors distance on acoustic (0.95m)
        assertEquals(0.95f, dimState.fusedDistanceMeters, 0.01f)

        // 3. Dark room (2 lux) -> ECHO mode
        val darkState = engine.fuse(
            ambientLux = 2f,
            cameraObstacle = null,
            cameraConfidence = 0.1f,
            visualDistanceMeters = null,
            acousticDistanceMeters = 0.82f,
            acousticConfidenceScore = 0.91f,
            isAcousticReliable = true
        )
        assertEquals(SensingMode.ECHO, darkState.activeMode)
        assertEquals(0.82f, darkState.fusedDistanceMeters, 0.01f)
        assertEquals("0.82 m", darkState.formattedDistance)
        assertTrue(darkState.confidenceScore > 0.8f)
    }

    @Test
    fun testManualEchoOverride() {
        val engine = SensorFusionEngine()

        val state = engine.fuse(
            ambientLux = 500f, // very bright
            cameraObstacle = "Table",
            cameraConfidence = 0.95f,
            visualDistanceMeters = 2.0f,
            acousticDistanceMeters = 1.1f,
            acousticConfidenceScore = 0.85f,
            isAcousticReliable = true,
            isManualEchoOverride = true // manual override
        )

        assertEquals(SensingMode.ECHO, state.activeMode)
        assertEquals(1.1f, state.fusedDistanceMeters, 0.01f)
    }
}
