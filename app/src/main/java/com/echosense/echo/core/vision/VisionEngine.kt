package com.echosense.echo.core.vision

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs

enum class SteeringDirection(val displayName: String) {
    CLEAR("Path Clear"),
    TURN_LEFT("Turn Left"),
    TURN_RIGHT("Turn Right"),
    AHEAD_OBSTACLE("Obstacle Ahead - Turn Left or Right")
}

data class DetectedVisualObstacle(
    val label: String,
    val confidence: Float,
    val estimatedDistanceMeters: Float?,
    val boundingBox: RectF?,
    val isReliable: Boolean,
    val steeringDirection: SteeringDirection = SteeringDirection.CLEAR,
    val horizontalCol: Int = -1
)

/**
 * Lightweight, on-device vision engine analyzing camera frames for obstacle presence,
 * spatial bounding, horizontal placement, and distance estimation with temporal noise filtering.
 */
class VisionEngine {

    // Temporal smoothing buffers to prevent single-frame noise jumps
    private var smoothedDistance: Float? = null
    private var consecutiveObstacleFrames = 0
    private var consecutiveClearFrames = 0

    fun reset() {
        smoothedDistance = null
        consecutiveObstacleFrames = 0
        consecutiveClearFrames = 0
    }

    /**
     * Analyzes an incoming CameraX ImageProxy (YUV_420_888).
     * Extracts luminance plane Y to evaluate contrast, edge density, and obstacle proximity.
     */
    fun analyzeFrame(imageProxy: ImageProxy, ambientLux: Float): DetectedVisualObstacle {
        val yPlane = imageProxy.planes[0]
        val buffer: ByteBuffer = yPlane.buffer
        val width = imageProxy.width
        val height = imageProxy.height
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride

        // If ambient light is too low, visual perception is inherently unreliable
        if (ambientLux < 5.0f) {
            reset()
            return DetectedVisualObstacle(
                label = "Low Light Obstacle",
                confidence = 0.10f,
                estimatedDistanceMeters = null,
                boundingBox = null,
                isReliable = false,
                steeringDirection = SteeringDirection.CLEAR
            )
        }

        val gridCols = 16
        val gridRows = 16
        val colStep = width / gridCols
        val rowStep = height / gridRows

        var totalContrast = 0.0
        var maxLocalContrast = 0.0
        var obstacleRow = -1
        var obstacleCol = -1

        var highContrastBlockCount = 0

        for (r in 1 until gridRows - 1) {
            for (c in 1 until gridCols - 1) {
                val pixelX = c * colStep
                val pixelY = r * rowStep
                val centerVal = getLuma(buffer, pixelX, pixelY, rowStride, pixelStride)
                val topVal = getLuma(buffer, pixelX, pixelY - rowStep, rowStride, pixelStride)
                val bottomVal = getLuma(buffer, pixelX, pixelY + rowStep, rowStride, pixelStride)
                val leftVal = getLuma(buffer, pixelX - colStep, pixelY, rowStride, pixelStride)
                val rightVal = getLuma(buffer, pixelX + colStep, pixelY, rowStride, pixelStride)

                val gradX = abs(rightVal - leftVal)
                val gradY = abs(bottomVal - topVal)
                val localContrast = (gradX + gradY).toDouble()

                totalContrast += localContrast

                if (localContrast > 90.0) {
                    highContrastBlockCount++
                }

                if (localContrast > maxLocalContrast) {
                    maxLocalContrast = localContrast
                    obstacleRow = r
                    obstacleCol = c
                }
            }
        }

        val totalBlocks = (gridRows - 2) * (gridCols - 2)
        val avgContrast = totalContrast / totalBlocks

        // Enhanced noise rejection heuristic:
        // Single isolated noise blocks won't trigger obstacle detection.
        // True physical obstacles require prominent local gradient (>100) AND block cluster density (>=2) OR strong average contrast (>22).
        val rawHasObstacle = maxLocalContrast > 105.0 && (highContrastBlockCount >= 2 || avgContrast > 22.0)

        if (!rawHasObstacle) {
            consecutiveClearFrames++
            consecutiveObstacleFrames = 0

            // Require 2 consecutive clear frames before clearing distance state to avoid rapid zone flickering
            if (consecutiveClearFrames >= 2) {
                smoothedDistance = null
            }

            return DetectedVisualObstacle(
                label = "Clear",
                confidence = 0.85f,
                estimatedDistanceMeters = smoothedDistance,
                boundingBox = null,
                isReliable = true,
                steeringDirection = SteeringDirection.CLEAR
            )
        }

        consecutiveObstacleFrames++
        consecutiveClearFrames = 0

        // Vertical position heuristic: lower in camera frame indicates closer object on the ground
        val verticalFraction = obstacleRow.toFloat() / gridRows
        val rawDist = when {
            verticalFraction > 0.70f -> 0.5f + (1.0f - verticalFraction) * 1.5f // 0.5m - 0.95m
            verticalFraction > 0.40f -> 0.95f + (0.7f - verticalFraction) * 2.5f // 0.95m - 1.7m
            else -> 1.7f + (0.4f - verticalFraction) * 3.0f // > 1.7m
        }

        // Exponential moving average for stable distance readout
        val prevDist = smoothedDistance
        val finalDist = if (prevDist == null || consecutiveObstacleFrames <= 1) {
            rawDist
        } else {
            0.6f * prevDist + 0.4f * rawDist
        }
        smoothedDistance = finalDist

        // Semantic labeling based on size & position
        val label = when {
            verticalFraction > 0.65f -> "Chair / Low Furniture"
            verticalFraction > 0.45f -> "Table / Countertop"
            maxLocalContrast > 150.0 -> "Person / Obstacle"
            else -> "Wall / Door"
        }

        // Horizontal column determination for directional steering (0..15 grid)
        // 0..5 = Left side -> Turn Right
        // 10..15 = Right side -> Turn Left
        // 6..9 = Center -> Obstacle Ahead (Turn Left or Right)
        val steeringDir = when {
            obstacleCol in 0..5 -> SteeringDirection.TURN_RIGHT
            obstacleCol in 10..15 -> SteeringDirection.TURN_LEFT
            else -> SteeringDirection.AHEAD_OBSTACLE
        }

        // Confidence scale
        val lightFactor = (ambientLux / 100f).coerceIn(0.2f, 1.0f)
        val contrastFactor = (maxLocalContrast / 180.0).toFloat().coerceIn(0.2f, 1.0f)
        val frameStabilityFactor = (consecutiveObstacleFrames / 3.0f).coerceIn(0.3f, 1.0f)
        val confidence = (0.35f * lightFactor + 0.35f * contrastFactor + 0.30f * frameStabilityFactor).coerceIn(0.1f, 0.95f)

        val boxWidth = 0.4f
        val boxHeight = 0.4f
        val left = ((obstacleCol.toFloat() / gridCols) - boxWidth / 2f).coerceIn(0f, 1f - boxWidth)
        val top = ((obstacleRow.toFloat() / gridRows) - boxHeight / 2f).coerceIn(0f, 1f - boxHeight)
        val boundingBox = RectF(left, top, left + boxWidth, top + boxHeight)

        return DetectedVisualObstacle(
            label = label,
            confidence = confidence,
            estimatedDistanceMeters = finalDist,
            boundingBox = boundingBox,
            isReliable = confidence >= 0.45f,
            steeringDirection = steeringDir,
            horizontalCol = obstacleCol
        )
    }

    private fun getLuma(buffer: ByteBuffer, x: Int, y: Int, rowStride: Int, pixelStride: Int): Int {
        val index = y * rowStride + x * pixelStride
        return if (index in 0 until buffer.capacity()) {
            buffer.get(index).toInt() and 0xFF
        } else {
            128
        }
    }
}
