package com.echosense.echo.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echosense.echo.ui.theme.EchoBackground
import com.echosense.echo.ui.theme.EchoBorder
import com.echosense.echo.ui.theme.EchoCyan
import com.echosense.echo.ui.theme.EchoGreen
import com.echosense.echo.ui.theme.EchoRed
import com.echosense.echo.ui.theme.EchoSurface
import com.echosense.echo.ui.theme.EchoTextSecondary
import com.echosense.echo.ui.theme.EchoWhite
import com.echosense.echo.ui.viewmodel.AppScreen
import com.echosense.echo.ui.viewmodel.EchoViewModel
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    viewModel: EchoViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val acousticResult by viewModel.acousticResult.collectAsState()
    val hardwareCap by viewModel.hardwareCapability.collectAsState()
    val deviceReport by viewModel.deviceReport.collectAsState()
    val fusedState by viewModel.fusedState.collectAsState()
    val motionData by viewModel.motionData.collectAsState()
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
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateTo(AppScreen.MAIN) }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = EchoCyan)
            }
            Text(
                text = "TECHNICAL DIAGNOSTICS",
                color = EchoWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        // Device & Audio Hardware Overview
        DiagnosticCard(title = "HARDWARE & CAPABILITIES") {
            DiagnosticRow("Device Model", deviceReport.deviceModel)
            DiagnosticRow("Android OS", deviceReport.androidVersion)
            DiagnosticRow("Sample Rate", "${hardwareCap.selectedSampleRate} Hz")
            DiagnosticRow("Active Chirp Band", "${hardwareCap.activeStartFreqHz.toInt()}-${hardwareCap.activeEndFreqHz.toInt()} Hz")
            DiagnosticRow("Near-Ultrasound Usable", if (hardwareCap.isNearUltrasoundUsable) "YES (18-21 kHz)" else "NO (Fallback 15-18 kHz)")
            DiagnosticRow("Validation Status", hardwareCap.validationStatus)
        }

        // Live DSP Telemetry
        DiagnosticCard(title = "ACOUSTIC SIGNAL PROCESSING (DSP)") {
            DiagnosticRow("Direct Path Delay", String.format(Locale.US, "%.2f ms", acousticResult.directPathDelayMs))
            DiagnosticRow("Round Trip Delay", String.format(Locale.US, "%.2f ms", acousticResult.echoDelayMs))
            DiagnosticRow("Calculated Distance", String.format(Locale.US, "%.3f m", acousticResult.distanceMeters))
            DiagnosticRow("Peak Correlation (Rxy)", String.format(Locale.US, "%.3f", acousticResult.peakCorrelation))
            DiagnosticRow("Signal-to-Noise (SNR)", String.format(Locale.US, "%.1f dB", acousticResult.snrDb))
            DiagnosticRow("Peak-to-Sidelobe (PSR)", String.format(Locale.US, "%.2f", acousticResult.psr))
            DiagnosticRow("Confidence Score", "${(acousticResult.confidence.score * 100).toInt()}% (${acousticResult.confidence.rating.name})")
            DiagnosticRow("DSP Status Message", acousticResult.statusMessage)
        }

        // Environmental & Sensor Telemetry
        DiagnosticCard(title = "ENVIRONMENT & MOTION") {
            DiagnosticRow("Ambient Light", String.format(Locale.US, "%.1f lux", fusedState.ambientLux))
            DiagnosticRow("Motion Activity", motionData.state.displayName)
            DiagnosticRow("Device Azimuth", String.format(Locale.US, "%.1f°", motionData.azimuthDegrees))
            DiagnosticRow("Microphone Active", if (acousticResult.isReliable || acousticResult.statusMessage != "STOPPED") "ACTIVE" else "IDLE")
            DiagnosticRow("Camera Active", if (isCameraActive) "ACTIVE" else "OFF")
        }

        // Export Diagnostics Button
        Button(
            onClick = {
                val path = viewModel.exportDiagnostics()
                Toast.makeText(context, "Diagnostics exported:\n$path", Toast.LENGTH_LONG).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EchoCyan)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = EchoBackground)
            Spacer(modifier = Modifier.padding(start = 8.dp))
            Text("EXPORT DIAGNOSTICS LOG", color = EchoBackground, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DiagnosticCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EchoSurface)
            .border(1.dp, EchoBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = EchoCyan,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
fun DiagnosticRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = EchoTextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = EchoWhite,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}
