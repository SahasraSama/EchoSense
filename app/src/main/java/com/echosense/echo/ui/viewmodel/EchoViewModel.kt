package com.echosense.echo.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echosense.echo.core.audio.AcousticRangingEngine
import com.echosense.echo.core.audio.AcousticResult
import com.echosense.echo.core.audio.AudioHardwareCapability
import com.echosense.echo.core.calibration.CalibrationManager
import com.echosense.echo.core.calibration.CalibrationProgress
import com.echosense.echo.core.diagnostics.DiagnosticsManager
import com.echosense.echo.core.diagnostics.LiveDiagnosticSnapshot
import com.echosense.echo.core.feedback.HapticEngine
import com.echosense.echo.core.feedback.VoiceEngine
import com.echosense.echo.core.fusion.FusedAwarenessState
import com.echosense.echo.core.sensors.MotionActivityState
import com.echosense.echo.core.fusion.SensorFusionEngine
import com.echosense.echo.core.fusion.SpatialScanManager
import com.echosense.echo.core.fusion.SpatialScanMap
import com.echosense.echo.core.sensors.DeviceHardwareReport
import com.echosense.echo.core.sensors.HardwareCapabilityDetector
import com.echosense.echo.core.sensors.LightReading
import com.echosense.echo.core.sensors.LightSensorManager
import com.echosense.echo.core.sensors.MotionData
import com.echosense.echo.core.sensors.MotionManager
import com.echosense.echo.core.vision.CameraEngine
import com.echosense.echo.core.vision.DetectedVisualObstacle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class AppScreen {
    MAIN,
    DIAGNOSTICS,
    CALIBRATION,
    SENSOR_TEST,
    SPATIAL_SCAN,
    PRESENTATION_DEMO
}

class EchoViewModel(application: Application) : AndroidViewModel(application) {

    val acousticEngine = AcousticRangingEngine(application)
    val lightSensorManager = LightSensorManager(application)
    val motionManager = MotionManager(application)
    val cameraEngine = CameraEngine(application)
    val sensorFusionEngine = SensorFusionEngine()
    val hapticEngine = HapticEngine(application)
    val voiceEngine = VoiceEngine(application)
    val calibrationManager = CalibrationManager(application, acousticEngine)
    val diagnosticsManager = DiagnosticsManager(application)
    val spatialScanManager = SpatialScanManager()
    val hardwareDetector = HardwareCapabilityDetector(application)

    val fusedState: StateFlow<FusedAwarenessState> = sensorFusionEngine.fusedState
    val acousticResult: StateFlow<AcousticResult> = acousticEngine.latestResult
    val correlationCurve: StateFlow<FloatArray> = acousticEngine.correlationCurve
    val hardwareCapability: StateFlow<AudioHardwareCapability> = acousticEngine.hardwareCapability
    val lightReading: StateFlow<LightReading> = lightSensorManager.reading
    val motionData: StateFlow<MotionData> = motionManager.motionData
    val visualObstacle: StateFlow<DetectedVisualObstacle> = cameraEngine.visualObstacle
    val isCameraActive: StateFlow<Boolean> = cameraEngine.isCameraActive
    val calibrationProgress: StateFlow<CalibrationProgress> = calibrationManager.progress
    val spatialScanMap: StateFlow<SpatialScanMap> = spatialScanManager.scanMap

    private val _deviceReport = MutableStateFlow(hardwareDetector.detectCapabilities())
    val deviceReport: StateFlow<DeviceHardwareReport> = _deviceReport.asStateFlow()

    private val _currentScreen = MutableStateFlow(AppScreen.MAIN)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _isManualEchoOverride = MutableStateFlow(false)
    val isManualEchoOverride: StateFlow<Boolean> = _isManualEchoOverride.asStateFlow()

    private val _isAdaptiveEnabled = MutableStateFlow(true)
    val isAdaptiveEnabled: StateFlow<Boolean> = _isAdaptiveEnabled.asStateFlow()

    private val _isHapticsEnabled = MutableStateFlow(true)
    val isHapticsEnabled: StateFlow<Boolean> = _isHapticsEnabled.asStateFlow()

    private val _isVoiceEnabled = MutableStateFlow(true)
    val isVoiceEnabled: StateFlow<Boolean> = _isVoiceEnabled.asStateFlow()

    private val _isRangingActive = MutableStateFlow(false)
    val isRangingActive: StateFlow<Boolean> = _isRangingActive.asStateFlow()

    init {
        // Feed ambient lux to CameraEngine
        cameraEngine.ambientLuxProvider = { lightReading.value.smoothedLux }

        // Start sensor listeners
        lightSensorManager.startListening()
        motionManager.startListening()

        // Probe hardware on startup
        viewModelScope.launch(Dispatchers.Default) {
            acousticEngine.probeAndValidateHardware()
        }

        // Sensor fusion event loop
        viewModelScope.launch {
            acousticResult.collectLatest { acoustic ->
                val light = lightReading.value
                val vision = visualObstacle.value
                val motion = motionData.value

                // Adapt acoustic ping rate based on motion
                val pingInterval = when (motion.state) {
                    MotionActivityState.SCANNING -> 200L
                    MotionActivityState.MOVING -> 350L
                    MotionActivityState.HELD -> 500L
                    MotionActivityState.STATIONARY -> 1200L
                    MotionActivityState.POCKET_OR_FACE_DOWN -> 2500L
                }
                acousticEngine.setPingInterval(pingInterval)

                // Execute sensor fusion
                val state = sensorFusionEngine.fuse(
                    ambientLux = light.smoothedLux,
                    cameraObstacle = vision.label,
                    cameraConfidence = vision.confidence,
                    visualDistanceMeters = vision.estimatedDistanceMeters,
                    acousticDistanceMeters = acoustic.distanceMeters,
                    acousticConfidenceScore = acoustic.confidence.score,
                    isAcousticReliable = acoustic.isReliable,
                    motionState = motion.state.name,
                    isManualEchoOverride = _isManualEchoOverride.value,
                    isAdaptiveEnabled = _isAdaptiveEnabled.value,
                    visualSteering = vision.steeringDirection
                )

                // Trigger Haptic Feedback
                hapticEngine.updateState(state.obstacleState, viewModelScope)

                // Trigger Voice Announcement with directional turning advice
                voiceEngine.onStateChange(
                    state = state.obstacleState,
                    distanceMeters = state.fusedDistanceMeters,
                    steeringDirection = state.steeringDirection
                )

                // Update Spatial Scan Map
                spatialScanManager.onAcousticMeasurement(
                    distanceMeters = acoustic.distanceMeters,
                    confidenceScore = acoustic.confidence.score,
                    isReliable = acoustic.isReliable,
                    currentAzimuth = motion.azimuthDegrees
                )
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun toggleManualEchoOverride() {
        _isManualEchoOverride.value = !_isManualEchoOverride.value
    }

    fun toggleAdaptiveMode() {
        _isAdaptiveEnabled.value = !_isAdaptiveEnabled.value
    }

    fun toggleHaptics() {
        val newVal = !_isHapticsEnabled.value
        _isHapticsEnabled.value = newVal
        hapticEngine.isEnabled = newVal
        if (!newVal) hapticEngine.stop()
    }

    fun toggleVoice() {
        val newVal = !_isVoiceEnabled.value
        _isVoiceEnabled.value = newVal
        voiceEngine.isEnabled = newVal
    }

    fun startAcousticRanging() {
        acousticEngine.startRanging(viewModelScope)
        _isRangingActive.value = true
    }

    fun stopAcousticRanging() {
        acousticEngine.stopRanging()
        _isRangingActive.value = false
        hapticEngine.stop()
    }

    fun triggerCalibration() {
        viewModelScope.launch {
            calibrationManager.runCalibration()
        }
    }

    fun exportDiagnostics(): String {
        val snapshot = diagnosticsManager.buildSnapshot(
            acousticResult = acousticResult.value,
            hardwareCap = hardwareCapability.value,
            deviceReport = deviceReport.value,
            fusedState = fusedState.value,
            motionData = motionData.value,
            isCameraActive = isCameraActive.value
        )
        return diagnosticsManager.exportDiagnostics(snapshot)
    }

    override fun onCleared() {
        super.onCleared()
        lightSensorManager.stopListening()
        motionManager.stopListening()
        cameraEngine.stopCamera()
        acousticEngine.stopRanging()
        hapticEngine.stop()
        voiceEngine.shutdown()
    }
}
