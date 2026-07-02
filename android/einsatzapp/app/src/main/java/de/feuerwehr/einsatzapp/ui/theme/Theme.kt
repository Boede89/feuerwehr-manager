package de.feuerwehr.einsatzapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = FeuerwehrRot,
    onPrimary = Color.White,
    primaryContainer = FeuerwehrRotHell,
    onPrimaryContainer = FeuerwehrRotDunkel,
    secondary = FeuerwehrRotDunkel,
    onSecondary = Color.White,
    background = FeuerwehrBg,
    onBackground = FeuerwehrText,
    surface = FeuerwehrCard,
    onSurface = FeuerwehrText,
    surfaceVariant = Color(0xFFF8FAFC),
    onSurfaceVariant = FeuerwehrTextMuted,
    outline = FeuerwehrBorder,
    error = Color(0xFFEF4444),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF6B5E),
    onPrimary = Color(0xFF3B0A06),
    primaryContainer = Color(0xFF8B1A12),
    onPrimaryContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE2E8F0),
)

@Composable
fun FeuerwehrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
