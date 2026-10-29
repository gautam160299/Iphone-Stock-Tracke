package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The approved demo look: near-black ground, amber for alerts, green for stock
private val PulseColorScheme =
  darkColorScheme(
    primary = Color(0xFFF6B445),
    onPrimary = Color(0xFF0B0D12),
    primaryContainer = Color(0xFF3A2E14),
    onPrimaryContainer = Color(0xFFF6D79A),
    secondary = Color(0xFF3DDC97),
    onSecondary = Color(0xFF06261A),
    secondaryContainer = Color(0xFF173B2C),
    onSecondaryContainer = Color(0xFFB9F5D9),
    tertiary = Color(0xFF8AB4FF),
    onTertiary = Color(0xFF0B0D12),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFEEF1F6),
    surface = Color(0xFF0B0D12),
    onSurface = Color(0xFFEEF1F6),
    surfaceVariant = Color(0xFF1B2030),
    onSurfaceVariant = Color(0xFFA3ACBC),
    surfaceContainerLowest = Color(0xFF080A0E),
    surfaceContainerLow = Color(0xFF10141C),
    surfaceContainer = Color(0xFF141821),
    surfaceContainerHigh = Color(0xFF1B2030),
    surfaceContainerHighest = Color(0xFF232938),
    outline = Color(0xFF3A4357),
    outlineVariant = Color(0xFF1F2533),
    error = Color(0xFFFF7A7A),
    onError = Color(0xFF2A0B0B),
    inverseSurface = Color(0xFFEEF1F6),
    inverseOnSurface = Color(0xFF0B0D12),
    scrim = Color(0xFF000000),
  )

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = PulseColorScheme, typography = Typography, content = content)
}
