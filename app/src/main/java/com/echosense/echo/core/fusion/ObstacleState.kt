package com.echosense.echo.core.fusion

enum class ObstacleState(
    val displayName: String,
    val description: String
) {
    SAFE("SAFE", "No immediate obstacle detected (>2m)"),
    CAUTION("CAUTION", "Obstacle in buffer zone (1–2m)"),
    CLOSE("CLOSE", "Approaching obstacle (0.5–1m)"),
    VERY_CLOSE("VERY CLOSE", "Imminent obstacle (0.3–0.5m)"),
    CRITICAL("CRITICAL", "Obstacle immediately ahead (<0.3m)"),
    NO_OBSTACLE("CLEAR", "Path clear / no reliable echo")
}
