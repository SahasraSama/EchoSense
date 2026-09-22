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
import androidx.compose.material.icons.filled.CheckCircle
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
import com.echosense.echo.core.fusion.SensingMode
import com.echosense.echo.ui.theme.EchoAmber
import com.echosense.echo.ui.theme.EchoBackground
import com.echosense.echo.ui.theme.EchoBorder
import com.echosense.echo.ui.theme.EchoCyan
import com.echosense.echo.ui.theme.EchoGreen
import com.echosense.echo.ui.theme.EchoSurface
import com.echosense.echo.ui.theme.EchoTextSecondary
import com.echosense.echo.ui.theme.EchoWhite
import com.echosense.echo.ui.viewmodel.AppScreen
import com.echosense.echo.ui.viewmodel.EchoViewModel

@Composable
fun PresentationDemoScreen(
    viewModel: EchoViewModel,
    modifier: Modifier = Modifier
) {
    val fusedState by viewModel.fusedState.collectAsState()
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
                text = "HACKATHON DEMO WALKTHROUGH",
                color = EchoWhite,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        // Tagline & Principles
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EchoSurface)
                .border(1.dp, EchoBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ECHO",
                color = EchoCyan,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "\"When vision fades, sound takes over.\"",
                color = EchoWhite,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Adaptive obstacle awareness combining camera vision in bright light with real acoustic echo ranging in low light.",
                color = EchoTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp
            )
        }

        // Live Demo Stage Tracker
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EchoSurface)
                .border(1.dp, EchoBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "DEMONSTRATION PROTOCOL",
                color = EchoCyan,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            DemoStepRow(
                stepNum = 1,
                title = "Bright Room (Vision Active)",
                desc = "Camera perceives obstacles; ambient light > 50 lux.",
                isCurrent = fusedState.activeMode == SensingMode.VISION
            )

            DemoStepRow(
                stepNum = 2,
                title = "Dim Light (Hybrid Transition)",
                desc = "Ambient light falls (10–45 lux); acoustic & vision co-process.",
                isCurrent = fusedState.activeMode == SensingMode.HYBRID
            )

            DemoStepRow(
                stepNum = 3,
                title = "Darkness (Acoustic Takeover)",
                desc = "Ambient light < 5 lux; acoustic echo ranging becomes primary.",
                isCurrent = fusedState.activeMode == SensingMode.ECHO
            )

            DemoStepRow(
                stepNum = 4,
                title = "Obstacle Proximity & Haptics",
                desc = "Point at wall (0.5m – 2m); real distance updates with pulse haptics.",
                isCurrent = acousticResult.isReliable
            )
        }

        // Live Readout Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(EchoSurface)
                .border(1.dp, EchoBorder, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("LIVE DEMO VALUES", color = EchoCyan, style = MaterialTheme.typography.labelSmall)
            DiagnosticRow("Current Mode", "${fusedState.activeMode.name} MODE")
            DiagnosticRow("Ambient Light", "${fusedState.ambientLux.toInt()} lux")
            DiagnosticRow("Active Distance", fusedState.formattedDistance)
            DiagnosticRow("Obstacle State", fusedState.obstacleState.displayName)
            DiagnosticRow("Confidence", "${(fusedState.confidenceScore * 100).toInt()}%")
        }

        Button(
            onClick = { viewModel.navigateTo(AppScreen.DIAGNOSTICS) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EchoCyan)
        ) {
            Text("VIEW TECHNICAL DSP PROOF", color = EchoBackground, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DemoStepRow(
    stepNum: Int,
    title: String,
    desc: String,
    isCurrent: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isCurrent) EchoCyan else EchoBorder)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$stepNum",
                color = if (isCurrent) EchoBackground else EchoWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isCurrent) EchoCyan else EchoWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = desc,
                color = EchoTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
