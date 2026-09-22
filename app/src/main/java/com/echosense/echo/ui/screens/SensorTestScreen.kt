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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echosense.echo.core.fusion.ObstacleState
import com.echosense.echo.ui.theme.EchoAmber
import com.echosense.echo.ui.theme.EchoBackground
import com.echosense.echo.ui.theme.EchoBorder
import com.echosense.echo.ui.theme.EchoCyan
import com.echosense.echo.ui.theme.EchoGreen
import com.echosense.echo.ui.theme.EchoOrange
import com.echosense.echo.ui.theme.EchoRed
import com.echosense.echo.ui.theme.EchoSurface
import com.echosense.echo.ui.theme.EchoTextSecondary
import com.echosense.echo.ui.theme.EchoWhite
import com.echosense.echo.ui.viewmodel.AppScreen
import com.echosense.echo.ui.viewmodel.EchoViewModel

enum class TestStatus(val label: String, val color: Color) {
    PASS("PASS", EchoGreen),
    WARNING("WARNING", EchoAmber),
    UNAVAILABLE("UNAVAILABLE", EchoRed)
}

@Composable
fun SensorTestScreen(
    viewModel: EchoViewModel,
    modifier: Modifier = Modifier
) {
    val report by viewModel.deviceReport.collectAsState()
    val hardwareCap by viewModel.hardwareCapability.collectAsState()
    val lightReading by viewModel.lightReading.collectAsState()
    val motionData by viewModel.motionData.collectAsState()
    val acousticResult by viewModel.acousticResult.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EchoBackground)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(scrollState),
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
                text = "HARDWARE SENSOR TESTS",
                color = EchoWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Test Cards
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EchoSurface)
                .border(1.dp, EchoBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SensorTestItem(
                name = "MIC TEST",
                detail = "PCM 16-bit Mono @ ${hardwareCap.selectedSampleRate} Hz",
                status = if (report.hasMicrophone) TestStatus.PASS else TestStatus.UNAVAILABLE
            )

            SensorTestItem(
                name = "SPEAKER TEST",
                detail = "AudioTrack Low-Latency Static Mode",
                status = if (hardwareCap.selectedSampleRate > 0) TestStatus.PASS else TestStatus.UNAVAILABLE
            )

            SensorTestItem(
                name = "ACOUSTIC ECHO TEST",
                detail = if (hardwareCap.isNearUltrasoundUsable) "Near-Ultrasound 18-21 kHz Active" else if (hardwareCap.isFallbackUsable) "Fallback 15-18 kHz Active" else "No echo response",
                status = when {
                    hardwareCap.isNearUltrasoundUsable -> TestStatus.PASS
                    hardwareCap.isFallbackUsable -> TestStatus.WARNING
                    else -> TestStatus.UNAVAILABLE
                }
            )

            SensorTestItem(
                name = "LIGHT SENSOR TEST",
                detail = "${lightReading.smoothedLux.toInt()} lux (${lightReading.level.displayName})",
                status = if (report.hasLightSensor) TestStatus.PASS else TestStatus.UNAVAILABLE
            )

            SensorTestItem(
                name = "GYROSCOPE TEST",
                detail = "Angular velocity: ${String.format("%.2f", motionData.angularSpeed)} rad/s",
                status = if (report.hasGyroscope) TestStatus.PASS else TestStatus.UNAVAILABLE
            )

            SensorTestItem(
                name = "ACCELEROMETER TEST",
                detail = "Total Accel: ${String.format("%.1f", motionData.totalAcceleration)} m/s²",
                status = if (report.hasAccelerometer) TestStatus.PASS else TestStatus.UNAVAILABLE
            )

            SensorTestItem(
                name = "CAMERA TEST",
                detail = report.cameraResolution,
                status = if (report.hasCamera) TestStatus.PASS else TestStatus.UNAVAILABLE
            )

            SensorTestItem(
                name = "HAPTIC TEST",
                detail = if (report.hasAmplitudeControl) "Hardware Amplitude Control Supported" else "Basic Vibration Supported",
                status = if (report.hasHaptics) TestStatus.PASS else TestStatus.UNAVAILABLE
            )
        }

        // Interactive Haptic Test Suite
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EchoSurface)
                .border(1.dp, EchoBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "HAPTIC VOCABULARY TEST",
                color = EchoCyan,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "Tap each state to feel its distinct vibration signature.",
                color = EchoTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HapticTestButton("SAFE", EchoGreen, Modifier.weight(1f)) {
                    viewModel.hapticEngine.testPattern(ObstacleState.SAFE)
                }
                HapticTestButton("CAUTION", EchoAmber, Modifier.weight(1f)) {
                    viewModel.hapticEngine.testPattern(ObstacleState.CAUTION)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HapticTestButton("CLOSE", EchoOrange, Modifier.weight(1f)) {
                    viewModel.hapticEngine.testPattern(ObstacleState.CLOSE)
                }
                HapticTestButton("CRITICAL", EchoRed, Modifier.weight(1f)) {
                    viewModel.hapticEngine.testPattern(ObstacleState.CRITICAL)
                }
            }
        }
    }
}

@Composable
fun SensorTestItem(
    name: String,
    detail: String,
    status: TestStatus
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = EchoWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(detail, color = EchoTextSecondary, fontSize = 12.sp)
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(status.color.copy(alpha = 0.15f))
                .border(1.dp, status.color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = status.label,
                color = status.color,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun HapticTestButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
