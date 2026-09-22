package com.echosense.echo.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

enum class MotionActivityState(val displayName: String) {
    STATIONARY("STATIONARY"),
    HELD("BEING HELD"),
    MOVING("MOVING"),
    SCANNING("ACTIVELY SCANNING"),
    POCKET_OR_FACE_DOWN("POCKET / FACE DOWN")
}

data class MotionData(
    val state: MotionActivityState = MotionActivityState.HELD,
    val azimuthDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val totalAcceleration: Float = 9.8f,
    val angularSpeed: Float = 0f,
    val isAvailable: Boolean = true
)

/**
 * Monitors accelerometer and gyroscope sensors to detect user activity and orientation.
 * Adapts ping rates and disables chirps when face-down or pocketed.
 */
class MotionManager(
    private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _motionData = MutableStateFlow(MotionData(isAvailable = accelerometer != null))
    val motionData: StateFlow<MotionData> = _motionData.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var currentAccel = 9.8f
    private var currentGyroSpeed = 0f
    private var currentAzimuth = 0f
    private var currentPitch = 0f
    private var currentRoll = 0f

    fun startListening(): Boolean {
        if (sensorManager == null) return false
        var registeredAny = false

        accelerometer?.let {
            if (sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)) {
                registeredAny = true
            }
        }
        gyroscope?.let {
            if (sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)) {
                registeredAny = true
            }
        }
        rotationVector?.let {
            if (sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)) {
                registeredAny = true
            }
        }

        _motionData.value = _motionData.value.copy(isAvailable = registeredAny)
        return registeredAny
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                currentAccel = sqrt(ax * ax + ay * ay + az * az)

                // Check face down (screen pointing down, az < -7.0)
                if (az < -7.0f) {
                    updateState(MotionActivityState.POCKET_OR_FACE_DOWN)
                    return
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                currentGyroSpeed = sqrt(gx * gx + gy * gy + gz * gz)
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                // Convert radians to degrees
                currentAzimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (currentAzimuth < 0) currentAzimuth += 360f
                currentPitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                currentRoll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
            }
        }

        // Determine activity state based on accel and gyro
        val state = when {
            abs(currentAccel - 9.8f) < 0.15f && currentGyroSpeed < 0.04f -> MotionActivityState.STATIONARY
            currentGyroSpeed > 0.45f -> MotionActivityState.SCANNING
            abs(currentAccel - 9.8f) > 1.2f -> MotionActivityState.MOVING
            else -> MotionActivityState.HELD
        }
        updateState(state)
    }

    private fun updateState(state: MotionActivityState) {
        _motionData.value = MotionData(
            state = state,
            azimuthDegrees = currentAzimuth,
            pitchDegrees = currentPitch,
            rollDegrees = currentRoll,
            totalAcceleration = currentAccel,
            angularSpeed = currentGyroSpeed,
            isAvailable = true
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
