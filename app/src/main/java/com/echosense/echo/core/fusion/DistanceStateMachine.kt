package com.echosense.echo.core.fusion

/**
 * State machine for obstacle distance categorization with hysteresis.
 * Prevents rapid UI and haptic flapping when distances hover near threshold boundaries.
 */
class DistanceStateMachine(
    private val hysteresisBandMeters: Float = 0.06f
) {
    private var currentState: ObstacleState = ObstacleState.NO_OBSTACLE

    val state: ObstacleState
        get() = currentState

    fun reset() {
        currentState = ObstacleState.NO_OBSTACLE
    }

    /**
     * Updates and returns the new [ObstacleState] given a measured distance in meters.
     */
    fun update(distanceMeters: Float): ObstacleState {
        if (distanceMeters <= 0f) {
            currentState = ObstacleState.NO_OBSTACLE
            return currentState
        }

        currentState = when (currentState) {
            ObstacleState.NO_OBSTACLE -> {
                when {
                    distanceMeters < 0.35f -> ObstacleState.CRITICAL
                    distanceMeters < 0.55f -> ObstacleState.VERY_CLOSE
                    distanceMeters < 1.05f -> ObstacleState.CLOSE
                    distanceMeters < 2.05f -> ObstacleState.CAUTION
                    else -> ObstacleState.SAFE
                }
            }
            ObstacleState.CRITICAL -> {
                // To transition out of CRITICAL, distance must exceed threshold + hysteresis
                if (distanceMeters > 0.35f + hysteresisBandMeters) {
                    if (distanceMeters > 0.55f) ObstacleState.CLOSE else ObstacleState.VERY_CLOSE
                } else {
                    ObstacleState.CRITICAL
                }
            }
            ObstacleState.VERY_CLOSE -> {
                when {
                    distanceMeters < 0.35f - hysteresisBandMeters -> ObstacleState.CRITICAL
                    distanceMeters > 0.55f + hysteresisBandMeters -> ObstacleState.CLOSE
                    else -> ObstacleState.VERY_CLOSE
                }
            }
            ObstacleState.CLOSE -> {
                when {
                    distanceMeters < 0.55f - hysteresisBandMeters -> ObstacleState.VERY_CLOSE
                    distanceMeters > 1.05f + hysteresisBandMeters -> ObstacleState.CAUTION
                    else -> ObstacleState.CLOSE
                }
            }
            ObstacleState.CAUTION -> {
                when {
                    distanceMeters < 1.05f - hysteresisBandMeters -> ObstacleState.CLOSE
                    distanceMeters > 2.05f + hysteresisBandMeters -> ObstacleState.SAFE
                    else -> ObstacleState.CAUTION
                }
            }
            ObstacleState.SAFE -> {
                if (distanceMeters < 2.05f - hysteresisBandMeters) {
                    ObstacleState.CAUTION
                } else {
                    ObstacleState.SAFE
                }
            }
        }

        return currentState
    }
}
