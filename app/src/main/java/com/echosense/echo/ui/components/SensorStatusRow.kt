package com.echosense.echo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echosense.echo.ui.theme.EchoBorder
import com.echosense.echo.ui.theme.EchoCyan
import com.echosense.echo.ui.theme.EchoGreen
import com.echosense.echo.ui.theme.EchoSurface
import com.echosense.echo.ui.theme.EchoTextSecondary
import com.echosense.echo.ui.theme.EchoWhite
import java.util.Locale

@Composable
fun SensorStatusRow(
    isCameraActive: Boolean,
    isAcousticActive: Boolean,
    ambientLux: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SensorStatusChip(
            label = "CAMERA",
            value = if (isCameraActive) "ACTIVE" else "STANDBY",
            isActive = isCameraActive,
            modifier = Modifier.weight(1f)
        )

        SensorStatusChip(
            label = "ACOUSTIC",
            value = if (isAcousticActive) "ACTIVE" else "STANDBY",
            isActive = isAcousticActive,
            modifier = Modifier.weight(1f)
        )

        SensorStatusChip(
            label = "LIGHT",
            value = String.format(Locale.US, "%.1f lx", ambientLux),
            isActive = true,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SensorStatusChip(
    label: String,
    value: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(EchoSurface)
            .border(1.dp, EchoBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = EchoTextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp
            )
            Text(
                text = value,
                color = if (isActive) EchoCyan else EchoWhite.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
