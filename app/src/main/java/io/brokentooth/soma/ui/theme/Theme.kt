package io.brokentooth.soma.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SomaDarkColorScheme = darkColorScheme(
    primary = SomaAccentBlue,
    onPrimary = SomaBackground,
    secondary = SomaAccentPurple,
    onSecondary = SomaBackground,
    tertiary = SomaAccentGreen,
    onTertiary = SomaBackground,
    background = SomaBackground,
    onBackground = SomaTextPrimary,
    surface = SomaSurface,
    onSurface = SomaTextPrimary,
    surfaceVariant = SomaSurfaceElevated,
    onSurfaceVariant = SomaTextSecondary,
    outline = SomaBorder,
    error = SomaAccentRed,
    onError = SomaBackground
)

@Composable
fun SomaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SomaDarkColorScheme,
        typography = Typography,
        content = content
    )
}
