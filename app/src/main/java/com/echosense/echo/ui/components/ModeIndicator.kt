package com.echosense.echo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echosense.echo.core.fusion.SensingMode
import com.echosense.echo.ui.theme.EchoAmber
import com.echosense.echo.ui.theme.EchoBorder
import com.echosense.echo.ui.theme.EchoCyan
import com.echosense.echo.ui.theme.EchoGreen
import com.echosense.echo.ui.theme.EchoSurfaceElevated
import com.echosense.echo.ui.theme.EchoTextSecondary
import com.echosense.echo.ui.theme.EchoWhite

@Composable
fun ModeIndicator(
    activeMode: SensingMode,
    isAdaptive: Boolean,
    modifier: Modifier = Modifier
) {
    val modeColor by animateColorAsState(
        targetValue = when (activeMode) {
            SensingMode.VISION -> EchoGreen
            SensingMode.HYBRID -> EchoAmber
            SensingMode.ECHO -> EchoCyan
        },
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "modeColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EchoSurfaceElevated)
            .border(1.dp, EchoBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(modeColor)
            )
            Text(
                text = "${activeMode.name} MODE",
                color = EchoWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(modeColor.copy(alpha = 0.12f))
                .border(1.dp, modeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isAdaptive) "ADAPTIVE" else "MANUAL",
                color = modeColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
