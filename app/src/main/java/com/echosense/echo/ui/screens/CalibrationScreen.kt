package com.echosense.echo.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.echosense.echo.core.calibration.CalibrationStep
import com.echosense.echo.ui.theme.EchoBackground
import com.echosense.echo.ui.theme.EchoBorder
import com.echosense.echo.ui.theme.EchoCyan
import com.echosense.echo.ui.theme.EchoGreen
import com.echosense.echo.ui.theme.EchoSurface
import com.echosense.echo.ui.theme.EchoTextSecondary
import com.echosense.echo.ui.theme.EchoWhite
import com.echosense.echo.ui.viewmodel.AppScreen
import com.echosense.echo.ui.viewmodel.EchoViewModel
import java.util.Locale

@Composable
fun CalibrationScreen(
    viewModel: EchoViewModel,
    modifier: Modifier = Modifier
) {
    val progress by viewModel.calibrationProgress.collectAsState()
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
                text = "CALIBRATE ECHO",
                color = EchoWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Instructions Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EchoSurface)
                .border(1.dp, EchoBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "CALIBRATION INSTRUCTIONS",
                color = EchoCyan,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            InstructionStep(1, "Move to a quiet environment.")
            InstructionStep(2, "Point the phone toward a flat wall.")
            InstructionStep(3, "Keep the phone approximately 1 metre away.")
            InstructionStep(4, "Hold the phone steadily.")
            InstructionStep(5, "Tap CALIBRATE.")
        }

        // Calibration Progress / Status Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EchoSurface)
                .border(1.dp, EchoBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "STATUS: ${progress.message}",
                color = if (progress.step == CalibrationStep.COMPLETED) EchoGreen else EchoWhite,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            if (progress.step != CalibrationStep.NOT_STARTED && progress.step != CalibrationStep.COMPLETED) {
                LinearProgressIndicator(
                    progress = { progress.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = EchoCyan,
                    trackColor = EchoBorder
                )
            }

            progress.profile?.let { prof ->
                if (prof.isCalibrated) {
                    Spacer(modifier = Modifier.height(4.dp))
                    DiagnosticRow("Calibrated Band", prof.calibratedFrequencyBand)
                    DiagnosticRow("Direct Path Latency", String.format(Locale.US, "%.2f ms", prof.directPathDelayMs))
                    DiagnosticRow("1m Reference Delay", String.format(Locale.US, "%.2f ms", prof.oneMeterEchoDelayMs))
                    DiagnosticRow("Baseline Correlation", String.format(Locale.US, "%.2f", prof.peakCorrelationBaseline))
                }
            }
        }

        // Calibrate Button
        Button(
            onClick = { viewModel.triggerCalibration() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EchoCyan)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = EchoBackground)
            Spacer(modifier = Modifier.padding(start = 8.dp))
            Text("CALIBRATE", color = EchoBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
fun InstructionStep(stepNumber: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "$stepNumber.",
            color = EchoCyan,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            color = EchoWhite,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
