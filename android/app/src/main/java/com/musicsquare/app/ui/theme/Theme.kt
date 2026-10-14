package com.musicsquare.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF3D8BFD)
private val AccentSoft = Color(0xFF7EB6FF)
private val BgDark = Color(0xFF02030A)
private val Panel = Color(0xFF0E1224)
private val TextMain = Color(0xFFF5F6FF)
private val TextSub = Color(0xFFA1A6D0)

private val ColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF041229),
    secondary = AccentSoft,
    background = BgDark,
    surface = Panel,
    onBackground = TextMain,
    onSurface = TextMain,
    onSurfaceVariant = TextSub,
    outline = Color(0x33FFFFFF),
)

@Composable
fun MusicSquareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content,
    )
}
