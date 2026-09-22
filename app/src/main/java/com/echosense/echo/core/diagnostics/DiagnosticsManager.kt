package com.echosense.echo.core.diagnostics

import android.content.Context
import com.echosense.echo.core.audio.AcousticResult
import com.echosense.echo.core.audio.AudioHardwareCapability
import com.echosense.echo.core.fusion.FusedAwarenessState
import com.echosense.echo.core.sensors.DeviceHardwareReport
import com.echosense.echo.core.sensors.MotionData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LiveDiagnosticSnapshot(
    val timestamp: String,
    val deviceModel: String,
    val sensingMode: String,
    val sampleRateHz: Int,
    val activeFrequencyBand: String,
    val ambientLux: Float,
    val acousticDistanceMeters: Float,
    val echoDelayMs: Float,
    val directPathDelayMs: Float,
    val peakCorrelation: Float,
    val snrDb: Float,
    val psr: Float,
    val confidenceRating: String,
    val confidenceScore: Float,
    val obstacleState: String,
    val motionState: String,
    val isMicActive: Boolean,
    val isCameraActive: Boolean,
    val nearUltrasoundSupported: Boolean
)

/**
 * Diagnostics manager aggregating live telemetry across acoustic, sensor,
 * and vision engines for developer inspection and export.
 */
class DiagnosticsManager(
    private val context: Context
) {
    fun buildSnapshot(
        acousticResult: AcousticResult,
        hardwareCap: AudioHardwareCapability,
        deviceReport: DeviceHardwareReport,
        fusedState: FusedAwarenessState,
        motionData: MotionData,
        isCameraActive: Boolean
    ): LiveDiagnosticSnapshot {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return LiveDiagnosticSnapshot(
            timestamp = dateFormat.format(Date(acousticResult.timestampMs)),
            deviceModel = deviceReport.deviceModel,
            sensingMode = fusedState.activeMode.name,
            sampleRateHz = hardwareCap.selectedSampleRate,
            activeFrequencyBand = "${hardwareCap.activeStartFreqHz.toInt()}-${hardwareCap.activeEndFreqHz.toInt()} Hz",
            ambientLux = fusedState.ambientLux,
            acousticDistanceMeters = acousticResult.distanceMeters,
            echoDelayMs = acousticResult.echoDelayMs,
            directPathDelayMs = acousticResult.directPathDelayMs,
            peakCorrelation = acousticResult.peakCorrelation,
            snrDb = acousticResult.snrDb,
            psr = acousticResult.psr,
            confidenceRating = acousticResult.confidence.rating.name,
            confidenceScore = acousticResult.confidence.score,
            obstacleState = fusedState.obstacleState.displayName,
            motionState = motionData.state.displayName,
            isMicActive = acousticResult.isReliable || acousticResult.statusMessage != "STOPPED",
            isCameraActive = isCameraActive,
            nearUltrasoundSupported = hardwareCap.isNearUltrasoundUsable
        )
    }

    /**
     * Exports the latest diagnostic snapshot as a formatted text file.
     * Returns the file path or formatted string.
     */
    fun exportDiagnostics(snapshot: LiveDiagnosticSnapshot): String {
        val sb = StringBuilder()
        sb.appendLine("==============================================")
        sb.appendLine("ECHO LIVE ACOUSTIC & SENSOR DIAGNOSTICS")
        sb.appendLine("==============================================")
        sb.appendLine("Timestamp:              ${snapshot.timestamp}")
        sb.appendLine("Device Model:           ${snapshot.deviceModel}")
        sb.appendLine("Current Sensing Mode:   ${snapshot.sensingMode}")
        sb.appendLine("Obstacle State:         ${snapshot.obstacleState}")
        sb.appendLine("----------------------------------------------")
        sb.appendLine("ACOUSTIC DSP TELEMETRY")
        sb.appendLine("Sample Rate:            ${snapshot.sampleRateHz} Hz")
        sb.appendLine("Chirp Band:             ${snapshot.activeFrequencyBand}")
        sb.appendLine("Near-Ultrasound Valid:  ${snapshot.nearUltrasoundSupported}")
        sb.appendLine("Direct Path Delay:      ${snapshot.directPathDelayMs} ms")
        sb.appendLine("Round Trip Echo Delay:  ${snapshot.echoDelayMs} ms")
        sb.appendLine("Calculated Distance:    ${snapshot.acousticDistanceMeters} m")
        sb.appendLine("Peak Correlation (Rxy): ${snapshot.peakCorrelation}")
        sb.appendLine("Signal-to-Noise (SNR):  ${snapshot.snrDb} dB")
        sb.appendLine("Peak-to-Sidelobe (PSR): ${snapshot.psr}")
        sb.appendLine("Confidence Rating:      ${snapshot.confidenceRating} (${(snapshot.confidenceScore * 100).toInt()}%)")
        sb.appendLine("----------------------------------------------")
        sb.appendLine("ENVIRONMENT & SENSORS")
        sb.appendLine("Ambient Illumination:   ${snapshot.ambientLux} lux")
        sb.appendLine("Motion Activity:        ${snapshot.motionState}")
        sb.appendLine("Microphone Active:      ${snapshot.isMicActive}")
        sb.appendLine("Camera Active:          ${snapshot.isCameraActive}")
        sb.appendLine("==============================================")

        try {
            val file = File(context.cacheDir, "echo_diagnostic_${System.currentTimeMillis()}.txt")
            file.writeText(sb.toString())
            return file.absolutePath
        } catch (e: Exception) {
            return sb.toString()
        }
    }
}
