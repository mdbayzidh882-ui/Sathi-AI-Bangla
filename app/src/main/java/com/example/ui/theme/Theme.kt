package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val SathiLightColorScheme = lightColorScheme(
    primary = SathiAmberPrimary,
    onPrimary = Color.White,
    primaryContainer = SathiGoldenSoft,
    onPrimaryContainer = SathiOnGoldenContainer,
    secondary = SathiSunHoney,
    onSecondary = Color.White,
    secondaryContainer = SathiHoneyContainer,
    onSecondaryContainer = SathiOnHoneyContainer,
    tertiary = SathiTealAccent,
    onTertiary = Color.White,
    tertiaryContainer = SathiTealContainer,
    onTertiaryContainer = SathiOnTealContainer,
    background = SathiDayBg,
    onBackground = SathiDayInk,
    surface = SathiDaySurface,
    onSurface = SathiDayInk,
    surfaceVariant = SathiDayCard,
    onSurfaceVariant = SathiDayMuted,
    outline = SathiDayOutline
)

private val SathiDarkColorScheme = darkColorScheme(
    primary = SathiNightGold,
    onPrimary = Color(0xFF281C00),
    primaryContainer = Color(0xFF4A3700),
    onPrimaryContainer = SathiGoldenSoft,
    secondary = SathiNightHoney,
    onSecondary = Color(0xFF291B00),
    secondaryContainer = Color(0xFF3E3008),
    onSecondaryContainer = SathiHoneyContainer,
    tertiary = SathiNightTeal,
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF004F47),
    onTertiaryContainer = SathiTealContainer,
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
fun sathiHorizonBackgroundBrush(darkTheme: Boolean = isSystemInDarkTheme()): Brush {
    return if (darkTheme) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to SathiTopNightDark,
                0.35f to SathiNightBg,
                1.00f to SathiBottomNightDark
            )
        )
    } else {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to SathiTopYellowLight,
                0.14f to SathiWhitePureLight,
                1.00f to SathiWhitePureLight
            )
        )
    }
}

@Composable
fun SathiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SathiDarkColorScheme else SathiLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SathiTypography,
        shapes = SathiShapes,
        content = content
    )
}
