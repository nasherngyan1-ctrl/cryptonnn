package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val OkxTerminalColorScheme = darkColorScheme(
    primary = OkxNeonLime,
    onPrimary = Color(0xFF07090E),
    primaryContainer = OkxLimeDim,
    onPrimaryContainer = OkxNeonLime,
    secondary = AiCyan,
    onSecondary = Color(0xFF07090E),
    secondaryContainer = AiCyanDim,
    onSecondaryContainer = AiCyan,
    tertiary = BullishGreen,
    onTertiary = Color(0xFF07090E),
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianCardBorder,
    error = BearishRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = OkxTerminalColorScheme,
            typography = Typography,
            content = content
        )
    }
}
