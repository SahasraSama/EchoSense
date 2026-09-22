package com.echosense.echo.core.fusion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class SpatialSectorReading(
    val distanceMeters: Float = -1f,
    val formattedDistance: String = "--",
    val confidenceScore: Float = 0f,
    val isReliable: Boolean = false,
    val timestampMs: Long = 0L
)

data class SpatialScanMap(
    val leftSector: SpatialSectorReading = SpatialSectorReading(),
    val centerSector: SpatialSectorReading = SpatialSectorReading(),
    val rightSector: SpatialSectorReading = SpatialSectorReading(),
    val referenceAzimuthDegrees: Float = 0f
)

/**
 * Manages Acoustic Spatial Scan (Left / Center / Right) by correlating
 * acoustic distance measurements with device gyroscope orientation.
 */
class SpatialScanManager {

    private val _scanMap = MutableStateFlow(SpatialScanMap())
    val scanMap: StateFlow<SpatialScanMap> = _scanMap.asStateFlow()

    private var baselineAzimuth: Float? = null

    fun resetBaseline(currentAzimuth: Float) {
        baselineAzimuth = currentAzimuth
        _scanMap.value = SpatialScanMap(referenceAzimuthDegrees = currentAzimuth)
    }

    /**
     * Ingests a new acoustic measurement with current phone azimuth.
     */
    fun onAcousticMeasurement(distanceMeters: Float, confidenceScore: Float, isReliable: Boolean, currentAzimuth: Float) {
        val base = baselineAzimuth ?: run {
            baselineAzimuth = currentAzimuth
            currentAzimuth
        }

        // Relative delta angle (-180 to +180)
        var deltaAngle = currentAzimuth - base
        while (deltaAngle > 180f) deltaAngle -= 360f
        while (deltaAngle < -180f) deltaAngle += 360f

        val formatted = if (isReliable && distanceMeters > 0f) {
            String.format(Locale.US, "%.1fm", distanceMeters)
        } else {
            "--"
        }

        val reading = SpatialSectorReading(
            distanceMeters = distanceMeters,
            formattedDistance = formatted,
            confidenceScore = confidenceScore,
            isReliable = isReliable,
            timestampMs = System.currentTimeMillis()
        )

        val current = _scanMap.value
        _scanMap.value = when {
            deltaAngle < -15f -> current.copy(leftSector = reading)
            deltaAngle > 15f -> current.copy(rightSector = reading)
            else -> current.copy(centerSector = reading)
        }
    }
}
