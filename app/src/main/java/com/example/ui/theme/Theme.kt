package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SathiLightColorScheme = lightColorScheme(
    primary = SathiLavender,
    onPrimary = Color.White,
    primaryContainer = SathiLavenderContainer,
    onPrimaryContainer = SathiOnLavenderContainer,
    secondary = SathiOrchid,
    onSecondary = Color.White,
    secondaryContainer = SathiOrchidContainer,
    onSecondaryContainer = SathiOnOrchidContainer,
    tertiary = SathiPeriwinkle,
    onTertiary = Color.White,
    tertiaryContainer = SathiPeriwinkleContainer,
    onTertiaryContainer = SathiOnPeriwinkleContainer,
    background = SathiMistBg,
    onBackground = SathiPlumInk,
    surface = SathiMistSurface,
    onSurface = SathiPlumInk,
    surfaceVariant = SathiMistCard,
    onSurfaceVariant = SathiMutedPlum,
    outline = SathiMistOutline
)

private val SathiDarkColorScheme = darkColorScheme(
    primary = SathiNightLavender,
    onPrimary = SathiOnLavenderContainer,
    primaryContainer = Color(0xFF3E2878),
    onPrimaryContainer = SathiLavenderContainer,
    secondary = SathiNightOrchid,
    onSecondary = SathiOnOrchidContainer,
    secondaryContainer = Color(0xFF4F246B),
    onSecondaryContainer = SathiOrchidContainer,
    tertiary = SathiNightPeriwinkle,
    onTertiary = SathiOnPeriwinkleContainer,
    tertiaryContainer = Color(0xFF28346B),
    onTertiaryContainer = SathiPeriwinkleContainer,
    background = SathiNightBg,
    onBackground = SathiNightInk,
    surface = SathiNightSurface,
    onSurface = SathiNightInk,
    surfaceVariant = SathiNightCard,
    onSurfaceVariant = SathiNightMuted,
    outline = SathiNightOutline
)

val SathiShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SathiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SathiDarkColorScheme else SathiLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SathiTypography,
        shapes = SathiShapes,
        content = content
    )
}
