package com.echosense.echo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = EchoCyan,
    secondary = EchoAmber,
    tertiary = EchoGreen,
    background = EchoBackground,
    surface = EchoSurface,
    onPrimary = EchoBackground,
    onSecondary = EchoBackground,
    onTertiary = EchoBackground,
    onBackground = EchoWhite,
    onSurface = EchoWhite,
    error = EchoRed
)

@Composable
fun EchoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
