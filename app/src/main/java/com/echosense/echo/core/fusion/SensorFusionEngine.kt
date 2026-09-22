package com.echosense.echo.core.fusion

import com.echosense.echo.core.audio.ConfidenceRating
import com.echosense.echo.core.vision.SteeringDirection
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * High-level obstacle awareness state output by the sensor fusion engine.
 */
data class FusedAwarenessState(
    val activeMode: SensingMode = SensingMode.VISION,
    val obstacleState: ObstacleState = ObstacleState.NO_OBSTACLE,
    val fusedDistanceMeters: Float = -1f,
    val formattedDistance: String = "CLEAR",
    val confidenceRating: ConfidenceRating = ConfidenceRating.UNRELIABLE,
    val confidenceScore: Float = 0f,
    val detectedLabel: String = "None",
    val ambientLux: Float = 100f,
    val modeTransitionEvent: ModeTransitionEvent? = null,
    val steeringDirection: SteeringDirection = SteeringDirection.CLEAR
)

/**
 * Deterministic sensor fusion engine integrating ambient light, camera vision,
 * acoustic ranging, and motion state to output the active sensing mode, fused distance, and steering direction.
 */
class SensorFusionEngine {

    private val distanceStateMachine = DistanceStateMachine()
    private var currentMode: SensingMode = SensingMode.VISION
    private var lastTransition: ModeTransitionEvent? = null

    private val _fusedState = MutableStateFlow(FusedAwarenessState())
    val fusedState: StateFlow<FusedAwarenessState> = _fusedState.asStateFlow()

    /**
     * Resets internal states.
     */
    fun reset() {
        distanceStateMachine.reset()
        currentMode = SensingMode.VISION
        lastTransition = null
    }

    /**
     * Executes deterministic sensor fusion tick.
     */
    fun fuse(
        ambientLux: Float,
        cameraObstacle: String?,
        cameraConfidence: Float,
        visualDistanceMeters: Float?,
        acousticDistanceMeters: Float,
        acousticConfidenceScore: Float,
        isAcousticReliable: Boolean,
        motionState: String = "HELD",
        isManualEchoOverride: Boolean = false,
        isAdaptiveEnabled: Boolean = true,
        visualSteering: SteeringDirection = SteeringDirection.CLEAR
    ): FusedAwarenessState {

        // 1. Determine Target Sensing Mode
        val targetMode: SensingMode = when {
            isManualEchoOverride -> SensingMode.ECHO
            !isAdaptiveEnabled -> SensingMode.VISION
            motionState == "POCKET" -> SensingMode.VISION // Suspend acoustic in pocket
            ambientLux < 5.0f || cameraConfidence < 0.25f -> SensingMode.ECHO
            ambientLux < 45.0f || cameraConfidence < 0.60f -> SensingMode.HYBRID
            else -> SensingMode.VISION
        }

        // Detect transition
        var transitionEvent: ModeTransitionEvent? = null
        if (targetMode != currentMode) {
            val reason = when {
                isManualEchoOverride -> "Manual ECHO Override"
                ambientLux < 5.0f -> "Low-light detected (< 5 lux)"
                ambientLux < 45.0f -> "Dim light detected (< 45 lux)"
                else -> "Adequate illumination restored"
            }
            transitionEvent = ModeTransitionEvent(currentMode, targetMode, reason)
            lastTransition = transitionEvent
            currentMode = targetMode
        }

        // 2. Compute Fused Distance and Confidence
        val fusedDistance: Float
        val fusedConfidenceScore: Float
        val fusedLabel: String

        when (currentMode) {
            SensingMode.VISION -> {
                fusedDistance = visualDistanceMeters ?: -1f
                fusedConfidenceScore = cameraConfidence
                fusedLabel = cameraObstacle ?: if (fusedDistance > 0f) "Obstacle" else "None"
            }
            SensingMode.HYBRID -> {
                // In Hybrid mode, if acoustic ranging is reliable, anchor on acoustic distance
                // while keeping camera's semantic label
                if (isAcousticReliable && acousticDistanceMeters > 0f) {
                    fusedDistance = acousticDistanceMeters
                    fusedConfidenceScore = (0.7f * acousticConfidenceScore + 0.3f * cameraConfidence).coerceIn(0f, 1f)
                    fusedLabel = cameraObstacle ?: "Obstacle"
                } else if (visualDistanceMeters != null && visualDistanceMeters > 0f) {
                    fusedDistance = visualDistanceMeters
                    fusedConfidenceScore = cameraConfidence * 0.7f // Dim light discount
                    fusedLabel = cameraObstacle ?: "Obstacle"
                } else {
                    fusedDistance = -1f
                    fusedConfidenceScore = 0f
                    fusedLabel = "None"
                }
            }
            SensingMode.ECHO -> {
                if (isAcousticReliable && acousticDistanceMeters > 0f) {
                    fusedDistance = acousticDistanceMeters
                    fusedConfidenceScore = acousticConfidenceScore
                    fusedLabel = "Obstacle"
                } else {
                    fusedDistance = -1f
                    fusedConfidenceScore = 0f
                    fusedLabel = "None"
                }
            }
        }

        // 3. Update Distance State Machine
        val obstacleState = distanceStateMachine.update(fusedDistance)

        // 4. Formatted Display String
        val formattedDist = when {
            fusedDistance <= 0f -> "NO RELIABLE ECHO"
            fusedConfidenceScore >= 0.70f -> String.format(Locale.US, "%.2f m", fusedDistance)
            fusedConfidenceScore >= 0.35f -> String.format(Locale.US, "~%.1f m", fusedDistance)
            else -> "NO RELIABLE ECHO"
        }

        val rating = when {
            fusedConfidenceScore >= 0.75f -> ConfidenceRating.HIGH
            fusedConfidenceScore >= 0.45f -> ConfidenceRating.MEDIUM
            fusedConfidenceScore >= 0.20f -> ConfidenceRating.LOW
            else -> ConfidenceRating.UNRELIABLE
        }

        // Determine effective steering direction
        val effectiveSteering = if (obstacleState == ObstacleState.NO_OBSTACLE || obstacleState == ObstacleState.SAFE) {
            SteeringDirection.CLEAR
        } else {
            visualSteering
        }

        val state = FusedAwarenessState(
            activeMode = currentMode,
            obstacleState = obstacleState,
            fusedDistanceMeters = fusedDistance,
            formattedDistance = formattedDist,
            confidenceRating = rating,
            confidenceScore = fusedConfidenceScore,
            detectedLabel = fusedLabel,
            ambientLux = ambientLux,
            modeTransitionEvent = transitionEvent,
            steeringDirection = effectiveSteering
        )

        _fusedState.value = state
        return state
    }
}
