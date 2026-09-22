package com.echosense.echo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echosense.echo.core.audio.ConfidenceRating
import com.echosense.echo.core.fusion.ObstacleState
import com.echosense.echo.core.vision.SteeringDirection
import com.echosense.echo.ui.theme.EchoAmber
import com.echosense.echo.ui.theme.EchoBorder
import com.echosense.echo.ui.theme.EchoCyan
import com.echosense.echo.ui.theme.EchoGray
import com.echosense.echo.ui.theme.EchoGreen
import com.echosense.echo.ui.theme.EchoOrange
import com.echosense.echo.ui.theme.EchoRed
import com.echosense.echo.ui.theme.EchoSurface
import com.echosense.echo.ui.theme.EchoTextSecondary
import com.echosense.echo.ui.theme.EchoWhite

@Composable
fun DistanceDisplay(
    distanceText: String,
    obstacleState: ObstacleState,
    confidenceRating: ConfidenceRating,
    detectedLabel: String,
    modifier: Modifier = Modifier,
    steeringDirection: SteeringDirection = SteeringDirection.CLEAR
) {
    val stateColor by animateColorAsState(
        targetValue = when (obstacleState) {
            ObstacleState.SAFE -> EchoGreen
            ObstacleState.CAUTION -> EchoAmber
            ObstacleState.CLOSE -> EchoOrange
            ObstacleState.VERY_CLOSE -> EchoRed
            ObstacleState.CRITICAL -> EchoRed
            ObstacleState.NO_OBSTACLE -> EchoCyan
        },
        label = "stateColor"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(EchoSurface)
            .border(1.dp, EchoBorder, RoundedCornerShape(24.dp))
            .padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Obstacle State Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(stateColor.copy(alpha = 0.15f))
                .border(1.dp, stateColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = obstacleState.displayName,
                color = stateColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Large Central Distance Readout
        Text(
            text = distanceText,
            color = if (distanceText == "NO RELIABLE ECHO") EchoTextSecondary else EchoWhite,
            style = if (distanceText.length > 8) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Directional Steering Advice Banner if obstacle detected
        if (steeringDirection != SteeringDirection.CLEAR && obstacleState != ObstacleState.NO_OBSTACLE && obstacleState != ObstacleState.SAFE) {
            val steerText = when (steeringDirection) {
                SteeringDirection.TURN_LEFT -> "⮌ TURN LEFT"
                SteeringDirection.TURN_RIGHT -> "TURN RIGHT ⮍"
                else -> "⚠ OBSTACLE AHEAD"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(EchoCyan.copy(alpha = 0.2f))
                    .border(1.dp, EchoCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = steerText,
                    color = EchoCyan,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Detected Semantic Label
        Text(
            text = detectedLabel.uppercase(),
            color = EchoTextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Confidence Dots & Rating
        ConfidenceDotsRow(
            rating = confidenceRating,
            tint = stateColor
        )
    }
}

@Composable
fun ConfidenceDotsRow(
    rating: ConfidenceRating,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val filledDots = when (rating) {
        ConfidenceRating.HIGH -> 4
        ConfidenceRating.MEDIUM -> 3
        ConfidenceRating.LOW -> 1
        ConfidenceRating.UNRELIABLE -> 0
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (i in 0 until 4) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (i < filledDots) tint else EchoGray.copy(alpha = 0.3f))
            )
        }

        Text(
            text = "${rating.name} CONFIDENCE",
            color = EchoTextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
