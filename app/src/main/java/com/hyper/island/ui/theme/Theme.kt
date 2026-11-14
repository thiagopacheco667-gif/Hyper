package com.hyper.island.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object HyperColors {
    val Background = Color(0xFF000000)
    val Card = Color(0xFF141416)
    val CardBorder = Color(0xFF232327)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFF8E8E93)
    val Accent = Color(0xFF0A84FF)
    val Ok = Color(0xFF30D158)
    val Warn = Color(0xFFFF9F0A)
}

private val scheme = darkColorScheme(
    primary = HyperColors.Accent,
    onPrimary = Color.White,
    background = HyperColors.Background,
    onBackground = HyperColors.TextPrimary,
    surface = HyperColors.Card,
    onSurface = HyperColors.TextPrimary,
    onSurfaceVariant = HyperColors.TextSecondary,
)

@Composable
fun HyperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
