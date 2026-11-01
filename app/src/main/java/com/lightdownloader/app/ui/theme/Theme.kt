package com.lightdownloader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4F46E5),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF121212),
    secondaryContainer = Color(0xFF3730A3),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Color(0xFFC084FC),
    onTertiary = Color(0xFF121212),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF12141A),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF1A1D26),
    onSurfaceVariant = Color(0xFFB0B3C0),
    outline = Color(0xFF323850),
    error = Color(0xFFF87171),
    onError = Color(0xFFFFFFFF),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF6366F1),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC7D2FE),
    onSecondaryContainer = Color(0xFF312E81),
    tertiary = Color(0xFF9333EA),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F6FB),
    onBackground = Color(0xFF1A1A2E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFEEF0F7),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFC7D2FE),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun LightDownloaderTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
