package com.echosense.echo.core.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.echosense.echo.core.fusion.ObstacleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Haptic feedback engine providing distinct sensory vocabulary for obstacle proximity.
 */
class HapticEngine(
    private val context: Context
) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var hapticLoopJob: Job? = null
    private var currentState: ObstacleState = ObstacleState.NO_OBSTACLE
    var isEnabled: Boolean = true

    // Prevents background sensor fusion ticks from cancelling manual test vibrations immediately
    private var testOverrideUntilMs: Long = 0L

    /**
     * Updates the active haptic pattern based on current obstacle proximity.
     */
    fun updateState(state: ObstacleState, scope: CoroutineScope) {
        if (!isEnabled || vibrator?.hasVibrator() != true) {
            stop()
            return
        }

        // If a manual test pattern was recently triggered, prevent background loops from cancelling it
        if (System.currentTimeMillis() < testOverrideUntilMs) {
            return
        }

        if (state == currentState && hapticLoopJob?.isActive == true) return
        currentState = state

        hapticLoopJob?.cancel()

        if (state == ObstacleState.SAFE || state == ObstacleState.NO_OBSTACLE) {
            stopInternal()
            return
        }

        hapticLoopJob = scope.launch(Dispatchers.Default) {
            while (isActive && isEnabled) {
                when (currentState) {
                    ObstacleState.CAUTION -> {
                        // Gentle pulse: 120ms on, 800ms off
                        vibrateOnce(120, 120)
                        delay(800)
                    }
                    ObstacleState.CLOSE -> {
                        // Medium rhythmic pulse: 150ms on, 350ms off
                        vibrateOnce(150, 180)
                        delay(350)
                    }
                    ObstacleState.VERY_CLOSE -> {
                        // Rapid pulse: 120ms on, 120ms off
                        vibrateOnce(120, 220)
                        delay(120)
                    }
                    ObstacleState.CRITICAL -> {
                        // Strong continuous buzz: 200ms on, 50ms off
                        vibrateOnce(200, 255)
                        delay(50)
                    }
                    else -> delay(400)
                }
            }
        }
    }

    /**
     * Test trigger for the Haptic Test Screen.
     */
    fun testPattern(state: ObstacleState) {
        if (vibrator?.hasVibrator() != true) return

        // Prevent background sensor loops from calling stop() for 1.8 seconds so user can feel the test
        testOverrideUntilMs = System.currentTimeMillis() + 1800L

        hapticLoopJob?.cancel()
        hapticLoopJob = null
        currentState = state

        when (state) {
            ObstacleState.SAFE -> stopInternal()
            ObstacleState.CAUTION -> vibrateOnce(200, 120)
            ObstacleState.CLOSE -> vibrateOnce(300, 180)
            ObstacleState.VERY_CLOSE -> vibrateOnce(400, 220)
            ObstacleState.CRITICAL -> vibrateOnce(600, 255)
            ObstacleState.NO_OBSTACLE -> stopInternal()
        }
    }

    private fun vibrateOnce(durationMs: Long, amplitude: Int) {
        try {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effectiveAmp = if (vib.hasAmplitudeControl()) {
                    amplitude.coerceIn(1, 255)
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                val effect = VibrationEffect.createOneShot(durationMs, effectiveAmp)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // Ignore vibration permission or hardware exceptions
        }
    }

    private fun stopInternal() {
        hapticLoopJob?.cancel()
        hapticLoopJob = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun stop() {
        testOverrideUntilMs = 0L
        stopInternal()
    }
}
