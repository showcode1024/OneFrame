package io.github.showcode1024.oneframe.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF080E19)
val Panel = Color(0xFF182335)
val Mint = Color(0xFF73B9FF)
val Muted = Color(0xFF9AAAC0)
val Line = Color(0xFF293B51)

@Composable fun OneFrameTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Mint, onPrimary = Ink, secondary = Color(0xFF94C9E4),
        background = Ink, onBackground = Color(0xFFF0F5F3), surface = Panel,
        onSurface = Color(0xFFF0F5F3), surfaceVariant = Color(0xFF1D2A32),
        onSurfaceVariant = Muted, outline = Line, error = Color(0xFFFFB4AB),
    ), content = content)
}
