package com.echosense.echo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echosense.echo.ui.theme.EchoBorder
import com.echosense.echo.ui.theme.EchoCyan
import com.echosense.echo.ui.theme.EchoGreen
import com.echosense.echo.ui.theme.EchoRed
import com.echosense.echo.ui.theme.EchoSurface
import com.echosense.echo.ui.theme.EchoTextSecondary
import com.echosense.echo.ui.theme.EchoWhite

@Composable
fun AcousticWaveformVisualizer(
    correlation: FloatArray,
    peakCorrelation: Float,
    snrDb: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EchoSurface)
            .border(1.dp, EchoBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = "REAL-TIME CROSS-CORRELATION",
                color = EchoWhite,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "SNR: ${String.format("%.1f", snrDb)} dB",
                color = if (snrDb >= 8f) EchoGreen else EchoRed,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height

                // Draw baseline grid
                drawLine(
                    color = EchoBorder,
                    start = Offset(0f, h * 0.8f),
                    end = Offset(w, h * 0.8f),
                    strokeWidth = 1f
                )

                if (correlation.isNotEmpty()) {
                    val path = Path()
                    val step = (correlation.size.toFloat() / w).coerceAtLeast(1f)
                    var firstPoint = true

                    var x = 0f
                    while (x < w) {
                        val sampleIdx = (x * step).toInt().coerceIn(0, correlation.size - 1)
                        val value = correlation[sampleIdx].coerceIn(0f, 1f)
                        val y = h - (value * h * 0.9f)

                        if (firstPoint) {
                            path.moveTo(x, y)
                            firstPoint = false
                        } else {
                            path.lineTo(x, y)
                        }
                        x += 2f
                    }

                    drawPath(
                        path = path,
                        color = EchoCyan,
                        style = Stroke(width = 2.5f)
                    )
                }
            }
        }
    }
}
