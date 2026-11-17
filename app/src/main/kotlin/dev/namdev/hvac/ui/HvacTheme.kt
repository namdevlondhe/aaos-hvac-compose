package dev.namdev.hvac.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** In-vehicle UIs are dark by default to cut glare at night. */
private val CabinColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00222E),
    primaryContainer = Color(0xFF004D64),
    tertiaryContainer = Color(0xFF5A3E00),
    background = Color(0xFF0E1114),
    surfaceVariant = Color(0xFF1F252B),
)

@Composable
fun HvacTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CabinColors, content = content)
}
