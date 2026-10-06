package com.digiglobal.goldbill.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Gold for gold, grey for silver — used across the app. */
val GoldColor = Color(0xFFB8860B)
val SilverColor = Color(0xFF78808A)

private val LightColors = lightColorScheme(
    primary = Color(0xFFE3262F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFDE3E4),
    onPrimaryContainer = Color(0xFF5C0A0E),
    secondary = Color(0xFF1F1F1F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE4E5),
    onSecondaryContainer = Color(0xFF5C0A0E),
    tertiary = GoldColor,
    tertiaryContainer = Color(0xFFFFF1CC),
    background = Color(0xFFF7F7F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFEFEFF1),
    onSurfaceVariant = Color(0xFF55575E),
    outline = Color(0xFFC9CACF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A8F),
    onPrimary = Color(0xFF5C0A0E),
    primaryContainer = Color(0xFF8C1219),
    secondary = Color(0xFFE6E6E6),
    secondaryContainer = Color(0xFF5C0A0E),
    tertiary = Color(0xFFE6C15A),
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2B2B2E)
)

@Composable
fun GoldTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
