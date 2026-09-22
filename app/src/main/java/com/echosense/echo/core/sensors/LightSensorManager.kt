package com.echosense.echo.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IlluminationLevel(val displayName: String) {
    BRIGHT("BRIGHT"),
    DIM("DIM"),
    LOW_LIGHT("LOW LIGHT"),
    VERY_LOW_LIGHT("VERY LOW LIGHT")
}

data class LightReading(
    val lux: Float = 100f,
    val smoothedLux: Float = 100f,
    val level: IlluminationLevel = IlluminationLevel.BRIGHT,
    val isAvailable: Boolean = true
)

/**
 * Manages the device's ambient light sensor, providing real-time smoothed lux readings
 * and illumination state classification.
 */
class LightSensorManager(
    private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val _reading = MutableStateFlow(LightReading(isAvailable = lightSensor != null))
    val reading: StateFlow<LightReading> = _reading.asStateFlow()

    private var smoothedLux = 100f
    private val alpha = 0.25f // Smoothing factor

    fun startListening(): Boolean {
        if (lightSensor == null || sensorManager == null) {
            _reading.value = LightReading(isAvailable = false)
            return false
        }
        return sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_LIGHT) return

        val rawLux = event.values[0]
        smoothedLux = smoothedLux + alpha * (rawLux - smoothedLux)

        val level = when {
            smoothedLux > 50f -> IlluminationLevel.BRIGHT
            smoothedLux > 10f -> IlluminationLevel.DIM
            smoothedLux > 1f -> IlluminationLevel.LOW_LIGHT
            else -> IlluminationLevel.VERY_LOW_LIGHT
        }

        _reading.value = LightReading(
            lux = rawLux,
            smoothedLux = smoothedLux,
            level = level,
            isAvailable = true
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
