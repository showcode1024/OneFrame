package io.github.showcode1024.oneframe.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF101114)
val Panel = Color(0xFF222327)
val Mint = Color(0xFFFF9F2D)
val Muted = Color(0xFFA8A9AE)
val Line = Color(0xFF34353A)

@Composable fun OneFrameTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Mint, onPrimary = Ink, secondary = Color(0xFFFFC57B),
        background = Ink, onBackground = Color(0xFFF7F6F4), surface = Panel,
        onSurface = Color(0xFFF7F6F4), surfaceVariant = Color(0xFF2D2E32),
        onSurfaceVariant = Muted, outline = Line, error = Color(0xFFFFB4AB),
    ), content = content)
}
