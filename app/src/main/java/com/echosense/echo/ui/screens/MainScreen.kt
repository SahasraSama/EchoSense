package com.echosense.echo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echosense.echo.ui.components.AcousticWaveformVisualizer
import com.echosense.echo.ui.components.DistanceDisplay
import com.echosense.echo.ui.components.ModeIndicator
import com.echosense.echo.ui.components.ModeTransitionBanner
import com.echosense.echo.ui.components.SensorStatusRow
import com.echosense.echo.ui.theme.EchoBackground
import com.echosense.echo.ui.theme.EchoCyan
import com.echosense.echo.ui.theme.EchoRed
import com.echosense.echo.ui.theme.EchoSurfaceElevated
import com.echosense.echo.ui.theme.EchoWhite
import com.echosense.echo.ui.viewmodel.AppScreen
import com.echosense.echo.ui.viewmodel.EchoViewModel

@Composable
fun MainScreen(
    viewModel: EchoViewModel,
    modifier: Modifier = Modifier
) {
    val fusedState by viewModel.fusedState.collectAsState()
    val acousticResult by viewModel.acousticResult.collectAsState()
    val correlationCurve by viewModel.correlationCurve.collectAsState()
    val isAdaptive by viewModel.isAdaptiveEnabled.collectAsState()
    val isManualEcho by viewModel.isManualEchoOverride.collectAsState()
    val isRangingActive by viewModel.isRangingActive.collectAsState()
    val isCameraActive by viewModel.isCameraActive.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header & Screen Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ECHO",
                    color = EchoCyan,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "When vision fades, sound takes over.",
                    color = EchoWhite.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp
                )
            }

            Row {
                IconButton(onClick = { viewModel.navigateTo(AppScreen.SPATIAL_SCAN) }) {
                    Icon(Icons.Default.Sensors, contentDescription = "Spatial Scan", tint = EchoCyan)
                }
                IconButton(onClick = { viewModel.navigateTo(AppScreen.CALIBRATION) }) {
                    Icon(Icons.Default.CompassCalibration, contentDescription = "Calibration", tint = EchoCyan)
                }
                IconButton(onClick = { viewModel.navigateTo(AppScreen.DIAGNOSTICS) }) {
                    Icon(Icons.Default.Build, contentDescription = "Diagnostics", tint = EchoCyan)
                }
            }
        }

        // Mode Indicator Pill
        ModeIndicator(
            activeMode = fusedState.activeMode,
            isAdaptive = isAdaptive
        )

        // Mode Transition Banner
        ModeTransitionBanner(
            event = fusedState.modeTransitionEvent,
            visible = fusedState.modeTransitionEvent != null
        )

        // Primary Large Distance Readout
        DistanceDisplay(
            distanceText = fusedState.formattedDistance,
            obstacleState = fusedState.obstacleState,
            confidenceRating = fusedState.confidenceRating,
            detectedLabel = fusedState.detectedLabel,
            steeringDirection = fusedState.steeringDirection
        )

        // Sensor Status Chips
        SensorStatusRow(
            isCameraActive = isCameraActive,
            isAcousticActive = isRangingActive,
            ambientLux = fusedState.ambientLux
        )

        // Live DSP Waveform
        AcousticWaveformVisualizer(
            correlation = correlationCurve,
            peakCorrelation = acousticResult.peakCorrelation,
            snrDb = acousticResult.snrDb
        )

        // Primary Action Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (isRangingActive) viewModel.stopAcousticRanging() else viewModel.startAcousticRanging()
                },
                modifier = Modifier
                    .weight(1.2f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRangingActive) EchoRed else EchoCyan
                )
            ) {
                Icon(
                    imageVector = if (isRangingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.padding(start = 6.dp))
                Text(
                    text = if (isRangingActive) "STOP ECHO" else "START ECHO",
                    color = EchoBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = { viewModel.toggleManualEchoOverride() },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isManualEcho) EchoCyan else EchoWhite
                )
            ) {
                Text(
                    text = if (isManualEcho) "MANUAL ON" else "AUTO MODE",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }

        // Secondary Navigation Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.navigateTo(AppScreen.SENSOR_TEST) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SENSOR TESTS", fontSize = 11.sp, color = EchoWhite)
            }

            OutlinedButton(
                onClick = { viewModel.navigateTo(AppScreen.PRESENTATION_DEMO) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("DEMO WALKTHROUGH", fontSize = 11.sp, color = EchoCyan)
            }
        }
    }
}
