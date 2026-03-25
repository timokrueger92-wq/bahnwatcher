package com.bahnwatcher.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = BackgroundDark,
    primaryContainer = CyanDark,
    onPrimaryContainer = OnSurface,
    secondary = CyanDark,
    onSecondary = OnSurface,
    background = BackgroundDark,
    onBackground = OnSurface,
    surface = SurfaceDark,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceMuted,
    outline = Border,
    error = Error
)

@Composable
fun BahnWatcherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}
