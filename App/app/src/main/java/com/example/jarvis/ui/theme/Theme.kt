package com.example.jarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// One dark-blue palette on every device: no light variant and no Android 12 dynamic colour,
// which would otherwise replace the blues with the wallpaper's colours.
private val JarvisColorScheme = darkColorScheme(
    primary = JarvisColors.Azure,
    onPrimary = JarvisColors.NightDeep,
    secondary = JarvisColors.Cyan,
    onSecondary = JarvisColors.NightDeep,
    tertiary = JarvisColors.Periwinkle,
    background = JarvisColors.NightDeep,
    onBackground = JarvisColors.TextPrimary,
    surface = JarvisColors.Midnight,
    onSurface = JarvisColors.TextPrimary,
    surfaceVariant = JarvisColors.SurfaceRaised,
    onSurfaceVariant = JarvisColors.TextSecondary,
    outline = JarvisColors.Outline,
    error = JarvisColors.Error
)

@Composable
fun JARVISTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        content = content
    )
}
