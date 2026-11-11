package com.picassoworkshop.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CitroenRed,
    onPrimary = ChevronWhite,
    primaryContainer = CitroenRedDark,
    onPrimaryContainer = ChevronWhite,
    secondary = CyanAccent,
    onSecondary = WorkshopBackground,
    background = WorkshopBackground,
    onBackground = TextPrimary,
    surface = WorkshopSurface,
    onSurface = TextPrimary,
    surfaceVariant = WorkshopSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = WorkshopBorder
)

@Composable
fun PicassoWorkshopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
