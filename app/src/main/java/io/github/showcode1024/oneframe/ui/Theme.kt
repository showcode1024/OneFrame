package io.github.showcode1024.oneframe.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF090C13)
val Panel = Color(0xFF171C28)
val Mint = Color(0xFF66ADFF)
val Muted = Color(0xFF9CA7B8)
val Line = Color(0xFF2A3140)

@Composable fun OneFrameTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Mint, onPrimary = Ink, secondary = Color(0xFF94C9E4),
        background = Ink, onBackground = Color(0xFFF3F5FA), surface = Panel,
        onSurface = Color(0xFFF3F5FA), surfaceVariant = Color(0xFF232A37),
        onSurfaceVariant = Muted, outline = Line, error = Color(0xFFFFB4AB),
    ), content = content)
}
