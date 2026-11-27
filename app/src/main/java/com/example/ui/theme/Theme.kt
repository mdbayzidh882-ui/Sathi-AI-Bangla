package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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
import com.example.core.SathiColorTheme

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

private val SathiOceanColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF03192B),
    primaryContainer = Color(0xFF0E3960),
    onPrimaryContainer = Color(0xFFCFF2FF),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF051A36),
    secondaryContainer = Color(0xFF102E52),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = Color(0xFF2DD4BF),
    onTertiary = Color(0xFF002B25),
    tertiaryContainer = Color(0xFF0D4446),
    onTertiaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF051326),
    onBackground = Color(0xFFF0F7FF),
    surface = Color(0xFF091B36),
    onSurface = Color(0xFFF0F7FF),
    surfaceVariant = Color(0xFF0D2548),
    onSurfaceVariant = Color(0xFFA7C4E5),
    outline = Color(0xFF284D7B)
)

private val SathiAuroraColorScheme = darkColorScheme(
    primary = Color(0xFFC084FC),
    onPrimary = Color(0xFF260842),
    primaryContainer = Color(0xFF3B1566),
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF032036),
    secondaryContainer = Color(0xFF1B2358),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF022C22),
    tertiaryContainer = Color(0xFF064E3B),
    onTertiaryContainer = Color(0xFFD1FAE5),
    background = Color(0xFF110A24),
    onBackground = Color(0xFFF5F3FF),
    surface = Color(0xFF191032),
    onSurface = Color(0xFFF5F3FF),
    surfaceVariant = Color(0xFF231847),
    onSurfaceVariant = Color(0xFFC7B8EA),
    outline = Color(0xFF4C3985)
)

private val SathiEmeraldColorScheme = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF022C22),
    primaryContainer = Color(0xFF0B4635),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF002A25),
    secondaryContainer = Color(0xFF0E3E3B),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF2E1E00),
    tertiaryContainer = Color(0xFF473308),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF051813),
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF09221B),
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = Color(0xFF0F2F26),
    onSurfaceVariant = Color(0xFFA7D9C6),
    outline = Color(0xFF245E4E)
)

private val SathiRoseColorScheme = darkColorScheme(
    primary = Color(0xFFFB7185),
    onPrimary = Color(0xFF3B0613),
    primaryContainer = Color(0xFF5D1328),
    onPrimaryContainer = Color(0xFFFFE4E6),
    secondary = Color(0xFFF472B6),
    onSecondary = Color(0xFF3A0723),
    secondaryContainer = Color(0xFF531538),
    onSecondaryContainer = Color(0xFFFCE7F3),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF2E1E00),
    tertiaryContainer = Color(0xFF4D3208),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF1A0812),
    onBackground = Color(0xFFFFF1F2),
    surface = Color(0xFF240D19),
    onSurface = Color(0xFFFFF1F2),
    surfaceVariant = Color(0xFF311423),
    onSurfaceVariant = Color(0xFFE7B6C4),
    outline = Color(0xFF6B2D45)
)

private val SathiSunsetLightColorScheme = lightColorScheme(
    primary = Color(0xFFC84B17),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0CE),
    onPrimaryContainer = Color(0xFF441600),
    secondary = Color(0xFFE07A22),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFECD9),
    onSecondaryContainer = Color(0xFF452100),
    tertiary = Color(0xFFB43C5E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDE6),
    onTertiaryContainer = Color(0xFF3F0B1D),
    background = Color(0xFFFFFBF7),
    onBackground = Color(0xFF241610),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF241610),
    surfaceVariant = Color(0xFFFFF2E8),
    onSurfaceVariant = Color(0xFF6D4F40),
    outline = Color(0xFFF2CBB5)
)

val SathiShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

fun sathiColorSchemeFor(darkTheme: Boolean, colorTheme: SathiColorTheme): ColorScheme {
    return when (colorTheme) {
        SathiColorTheme.OCEAN -> SathiOceanColorScheme
        SathiColorTheme.AURORA_VIOLET -> SathiAuroraColorScheme
        SathiColorTheme.EMERALD_FOREST -> SathiEmeraldColorScheme
        SathiColorTheme.ROSE_CRIMSON -> SathiRoseColorScheme
        SathiColorTheme.SUNSET_AMBER -> if (darkTheme) SathiDarkColorScheme else SathiSunsetLightColorScheme
        SathiColorTheme.MIDNIGHT_NIGHT -> SathiDarkColorScheme
        SathiColorTheme.GOLDEN_DAY -> if (darkTheme) SathiDarkColorScheme else SathiLightColorScheme
    }
}

@Composable
fun sathiHorizonBackgroundBrush(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: SathiColorTheme = SathiColorTheme.GOLDEN_DAY
): Brush {
    return when (colorTheme) {
        SathiColorTheme.OCEAN -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF04142B),
                0.42f to Color(0xFF082246),
                1.00f to Color(0xFF030E20)
            )
        )
        SathiColorTheme.AURORA_VIOLET -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF1A0E38),
                0.45f to Color(0xFF130B29),
                1.00f to Color(0xFF0B061A)
            )
        )
        SathiColorTheme.EMERALD_FOREST -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF08261E),
                0.45f to Color(0xFF051A14),
                1.00f to Color(0xFF03100D)
            )
        )
        SathiColorTheme.ROSE_CRIMSON -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFF2D0D1D),
                0.45f to Color(0xFF1D0914),
                1.00f to Color(0xFF12050C)
            )
        )
        SathiColorTheme.SUNSET_AMBER -> if (darkTheme) {
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
                    0.00f to Color(0xFFFFE7D4),
                    0.18f to Color(0xFFFFFBF7),
                    1.00f to Color(0xFFFFFFFF)
                )
            )
        }
        SathiColorTheme.MIDNIGHT_NIGHT -> Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to SathiTopNightDark,
                0.35f to SathiNightBg,
                1.00f to SathiBottomNightDark
            )
        )
        SathiColorTheme.GOLDEN_DAY -> if (darkTheme) {
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
}

@Composable
fun SathiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: SathiColorTheme = SathiColorTheme.GOLDEN_DAY,
    content: @Composable () -> Unit
) {
    val effectiveDark = darkTheme || colorTheme.isDarkPalette
    val colorScheme = sathiColorSchemeFor(effectiveDark, colorTheme)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !effectiveDark
                controller.isAppearanceLightNavigationBars = !effectiveDark
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
