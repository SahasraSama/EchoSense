package com.echosense.echo.core.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager

data class DeviceHardwareReport(
    val hasMicrophone: Boolean,
    val micSampleRates: List<Int>,
    val hasLightSensor: Boolean,
    val lightSensorName: String,
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
    val hasCamera: Boolean,
    val cameraResolution: String,
    val hasHaptics: Boolean,
    val hasAmplitudeControl: Boolean,
    val supportsNearUltrasound: Boolean,
    val deviceModel: String,
    val androidVersion: String
)

/**
 * Detects and evaluates the physical hardware capabilities of the target device (e.g. iQOO 15).
 */
class HardwareCapabilityDetector(
    private val context: Context
) {
    fun detectCapabilities(): DeviceHardwareReport {
        val packageManager = context.packageManager
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

        // 1. Microphone & Sample Rates
        val hasMic = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val testRates = intArrayOf(44100, 48000, 96000, 192000)
        val supportedRates = mutableListOf<Int>()
        for (rate in testRates) {
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf > 0) {
                supportedRates.add(rate)
            }
        }
        val supportsNearUltrasound = supportedRates.any { it >= 48000 }

        // 2. Light Sensor
        val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        val hasLight = lightSensor != null
        val lightName = lightSensor?.name ?: "Unavailable"

        // 3. Accelerometer & Gyroscope
        val hasAccel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val hasGyro = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

        // 4. Camera
        var hasCam = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        var camResolution = "Unavailable"
        try {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val backCamId = camManager?.cameraIdList?.firstOrNull { id ->
                val chars = camManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            if (backCamId != null) {
                hasCam = true
                camResolution = "Back Camera ($backCamId)"
            }
        } catch (e: Exception) {
            // Graceful fallback
        }

        // 5. Vibration / Haptics
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val hasVibe = vibrator?.hasVibrator() == true
        val hasAmpControl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.hasAmplitudeControl() == true
        } else {
            false
        }

        return DeviceHardwareReport(
            hasMicrophone = hasMic,
            micSampleRates = supportedRates,
            hasLightSensor = hasLight,
            lightSensorName = lightName,
            hasAccelerometer = hasAccel,
            hasGyroscope = hasGyro,
            hasCamera = hasCam,
            cameraResolution = camResolution,
            hasHaptics = hasVibe,
            hasAmplitudeControl = hasAmpControl,
            supportsNearUltrasound = supportsNearUltrasound,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        )
    }
}
