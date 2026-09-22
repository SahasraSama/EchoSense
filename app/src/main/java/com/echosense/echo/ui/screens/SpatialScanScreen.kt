package com.echosense.echo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echosense.echo.ui.theme.EchoBackground
import com.echosense.echo.ui.theme.EchoBorder
import com.echosense.echo.ui.theme.EchoCyan
import com.echosense.echo.ui.theme.EchoGreen
import com.echosense.echo.ui.theme.EchoOrange
import com.echosense.echo.ui.theme.EchoSurface
import com.echosense.echo.ui.theme.EchoTextSecondary
import com.echosense.echo.ui.theme.EchoWhite
import com.echosense.echo.ui.viewmodel.AppScreen
import com.echosense.echo.ui.viewmodel.EchoViewModel
import java.util.Locale

@Composable
fun SpatialScanScreen(
    viewModel: EchoViewModel,
    modifier: Modifier = Modifier
) {
    val scanMap by viewModel.spatialScanMap.collectAsState()
    val motionData by viewModel.motionData.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateTo(AppScreen.MAIN) }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = EchoCyan)
            }
            Text(
                text = "ACOUSTIC SPATIAL SCAN",
                color = EchoWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "Pan your phone left and right across the room to map directional acoustic distances.",
            color = EchoTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp
        )

        // Radar / Sector Visualization
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SpatialSectorCard(
                sectorName = "LEFT",
                distanceText = scanMap.leftSector.formattedDistance,
                isReliable = scanMap.leftSector.isReliable,
                modifier = Modifier.weight(1f)
            )

            SpatialSectorCard(
                sectorName = "CENTER",
                distanceText = scanMap.centerSector.formattedDistance,
                isReliable = scanMap.centerSector.isReliable,
                modifier = Modifier.weight(1f)
            )

            SpatialSectorCard(
                sectorName = "RIGHT",
                distanceText = scanMap.rightSector.formattedDistance,
                isReliable = scanMap.rightSector.isReliable,
                modifier = Modifier.weight(1f)
            )
        }

        // Current Orientation Readout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EchoSurface)
                .border(1.dp, EchoBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CURRENT HEADING",
                    color = EchoTextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = String.format(Locale.US, "%.1f°", motionData.azimuthDegrees),
                    color = EchoCyan,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Reset Baseline Button
        Button(
            onClick = { viewModel.spatialScanManager.resetBaseline(motionData.azimuthDegrees) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EchoCyan)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = EchoBackground)
            Spacer(modifier = Modifier.padding(start = 8.dp))
            Text("RESET SCAN BASELINE", color = EchoBackground, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SpatialSectorCard(
    sectorName: String,
    distanceText: String,
    isReliable: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(EchoSurface)
            .border(
                width = if (isReliable) 1.5.dp else 1.dp,
                color = if (isReliable) EchoCyan else EchoBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 24.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = sectorName,
            color = EchoTextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Text(
            text = distanceText,
            color = if (isReliable) EchoWhite else EchoTextSecondary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isReliable) EchoGreen.copy(alpha = 0.2f) else EchoBorder)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isReliable) "LOCKED" else "SEARCH",
                color = if (isReliable) EchoGreen else EchoTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
