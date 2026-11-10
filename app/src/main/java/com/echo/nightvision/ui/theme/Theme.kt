package com.echo.nightvision.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightVisionColorScheme = lightColorScheme(
    primary = Color(0xFF006C59),
    onPrimary = Color.White,
    secondary = Color(0xFF637B4A),
    onSecondary = Color.White,
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF182C2B),
    surface = Color.White,
    onSurface = Color(0xFF182C2B),
    surfaceVariant = Color(0xFFEAF3EC),
    onSurfaceVariant = Color(0xFF586A64),
    outline = Color(0xFFDCE5E0)
)

@Composable
fun NightVisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightVisionColorScheme, typography = Typography, content = content)
}
