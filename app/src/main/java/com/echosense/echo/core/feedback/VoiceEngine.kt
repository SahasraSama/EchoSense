package com.echosense.echo.core.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import com.echosense.echo.core.fusion.ObstacleState
import com.echosense.echo.core.vision.SteeringDirection
import java.util.Locale

/**
 * Accessible audio guidance engine providing directional turn spoken cues.
 */
class VoiceEngine(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    var isEnabled: Boolean = true

    private var lastSpokenState: ObstacleState? = null
    private var lastSpokenSteering: SteeringDirection? = null
    private var lastSpokenTimeMs: Long = 0L

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(1.1f)
            tts?.setPitch(1.0f)
            isInitialized = true
        }
    }

    /**
     * Announces significant obstacle state changes with actionable turn cues.
     */
    fun onStateChange(
        state: ObstacleState,
        distanceMeters: Float,
        steeringDirection: SteeringDirection = SteeringDirection.CLEAR
    ) {
        if (!isEnabled || !isInitialized) return

        val now = System.currentTimeMillis()
        val directionChanged = steeringDirection != lastSpokenSteering && steeringDirection != SteeringDirection.CLEAR

        // Rate limit speech: at least 2.8s between identical cues unless CRITICAL or direction changes
        if (state == lastSpokenState && !directionChanged && (now - lastSpokenTimeMs) < 2800L) return
        if ((now - lastSpokenTimeMs) < 1600L && state != ObstacleState.CRITICAL) return

        lastSpokenState = state
        lastSpokenSteering = steeringDirection
        lastSpokenTimeMs = now

        val distanceStr = if (distanceMeters > 0f) String.format(Locale.US, "%.1f", distanceMeters) else ""

        val message = when (state) {
            ObstacleState.CRITICAL -> {
                when (steeringDirection) {
                    SteeringDirection.TURN_LEFT -> "Critical obstacle on right! Turn left now."
                    SteeringDirection.TURN_RIGHT -> "Critical obstacle on left! Turn right now."
                    else -> "Critical obstacle directly ahead! Stop."
                }
            }
            ObstacleState.VERY_CLOSE -> {
                when (steeringDirection) {
                    SteeringDirection.TURN_LEFT -> "Very close obstacle on right. Turn left."
                    SteeringDirection.TURN_RIGHT -> "Very close obstacle on left. Turn right."
                    else -> "Very close obstacle ahead. $distanceStr metres."
                }
            }
            ObstacleState.CLOSE -> {
                when (steeringDirection) {
                    SteeringDirection.TURN_LEFT -> "Obstacle on right. Turn left."
                    SteeringDirection.TURN_RIGHT -> "Obstacle on left. Turn right."
                    else -> "Obstacle ahead. $distanceStr metres."
                }
            }
            ObstacleState.CAUTION -> {
                when (steeringDirection) {
                    SteeringDirection.TURN_LEFT -> "Caution. Obstacle on right, turn left."
                    SteeringDirection.TURN_RIGHT -> "Caution. Obstacle on left, turn right."
                    else -> "Caution. Obstacle ahead."
                }
            }
            ObstacleState.SAFE -> "Path clear."
            ObstacleState.NO_OBSTACLE -> ""
        }

        if (message.isNotEmpty()) {
            speak(message)
        }
    }

    /**
     * Speaks an immediate custom announcement.
     */
    fun speak(text: String) {
        if (!isEnabled || !isInitialized) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "echo_guidance")
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // Ignore
        } finally {
            tts = null
            isInitialized = false
        }
    }
}
