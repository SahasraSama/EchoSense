package com.echosense.echo.core.fusion

enum class SensingMode {
    VISION,
    HYBRID,
    ECHO
}

data class ModeTransitionEvent(
    val fromMode: SensingMode,
    val toMode: SensingMode,
    val reason: String,
    val timestampMs: Long = System.currentTimeMillis()
)
