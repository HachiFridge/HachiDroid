package dev.hachifridge.hachidroid.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.hachifridge.hachidroid.core.PatchMode

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
    inverseSurface = md_theme_dark_inverseSurface,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    scrim = md_theme_dark_scrim,
    surfaceContainerLowest = md_theme_dark_surfaceContainerLowest,
    surfaceContainerLow = md_theme_dark_surfaceContainerLow,
    surfaceContainer = md_theme_dark_surfaceContainer,
    surfaceContainerHigh = md_theme_dark_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_dark_surfaceContainerHighest,
)

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
    inverseSurface = md_theme_light_inverseSurface,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceTint = md_theme_light_surfaceTint,
    scrim = md_theme_light_scrim,
    surfaceContainerLowest = md_theme_light_surfaceContainerLowest,
    surfaceContainerLow = md_theme_light_surfaceContainerLow,
    surfaceContainer = md_theme_light_surfaceContainer,
    surfaceContainerHigh = md_theme_light_surfaceContainerHigh,
    surfaceContainerHighest = md_theme_light_surfaceContainerHighest,
)

/** Softer, rounder shapes than the Material defaults; screens read them via MaterialTheme.shapes. */
private val HachiDroidShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HachiDroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is opt-in via Settings; the brand palettes are the default.
    dynamicColor: Boolean = false,
    // Each game family keeps its own identity: honey for Tracen, Pecorine's blue for Astraea.
    mode: PatchMode = PatchMode.TRACEN,
    content: @Composable () -> Unit
) {
    val astraea = mode == PatchMode.ASTRAEA
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> if (astraea) AstraeaDarkColorScheme else DarkColorScheme
        else -> if (astraea) AstraeaLightColorScheme else LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge: bars stay transparent, only the icon contrast flips.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = HachiDroidShapes,
        content = content
    )
}

// Astraea's identity: the same component roles, Pecorine's blue-and-gold instead of honey.
// Only the mode decides between the two sets; everything downstream keeps reading MaterialTheme.
private val AstraeaDarkColorScheme = darkColorScheme(
    primary = astraea_dark_primary,
    onPrimary = astraea_dark_onPrimary,
    primaryContainer = astraea_dark_primaryContainer,
    onPrimaryContainer = astraea_dark_onPrimaryContainer,
    secondary = astraea_dark_secondary,
    onSecondary = astraea_dark_onSecondary,
    secondaryContainer = astraea_dark_secondaryContainer,
    onSecondaryContainer = astraea_dark_onSecondaryContainer,
    tertiary = astraea_dark_tertiary,
    onTertiary = astraea_dark_onTertiary,
    tertiaryContainer = astraea_dark_tertiaryContainer,
    onTertiaryContainer = astraea_dark_onTertiaryContainer,
    error = astraea_dark_error,
    onError = astraea_dark_onError,
    errorContainer = astraea_dark_errorContainer,
    onErrorContainer = astraea_dark_onErrorContainer,
    background = astraea_dark_background,
    onBackground = astraea_dark_onBackground,
    surface = astraea_dark_surface,
    onSurface = astraea_dark_onSurface,
    surfaceVariant = astraea_dark_surfaceVariant,
    onSurfaceVariant = astraea_dark_onSurfaceVariant,
    outline = astraea_dark_outline,
    outlineVariant = astraea_dark_outlineVariant,
    inverseSurface = astraea_dark_inverseSurface,
    inverseOnSurface = astraea_dark_inverseOnSurface,
    inversePrimary = astraea_dark_inversePrimary,
    surfaceTint = astraea_dark_surfaceTint,
    scrim = astraea_dark_scrim,
    surfaceContainerLowest = astraea_dark_surfaceContainerLowest,
    surfaceContainerLow = astraea_dark_surfaceContainerLow,
    surfaceContainer = astraea_dark_surfaceContainer,
    surfaceContainerHigh = astraea_dark_surfaceContainerHigh,
    surfaceContainerHighest = astraea_dark_surfaceContainerHighest,
)

private val AstraeaLightColorScheme = lightColorScheme(
    primary = astraea_light_primary,
    onPrimary = astraea_light_onPrimary,
    primaryContainer = astraea_light_primaryContainer,
    onPrimaryContainer = astraea_light_onPrimaryContainer,
    secondary = astraea_light_secondary,
    onSecondary = astraea_light_onSecondary,
    secondaryContainer = astraea_light_secondaryContainer,
    onSecondaryContainer = astraea_light_onSecondaryContainer,
    tertiary = astraea_light_tertiary,
    onTertiary = astraea_light_onTertiary,
    tertiaryContainer = astraea_light_tertiaryContainer,
    onTertiaryContainer = astraea_light_onTertiaryContainer,
    error = astraea_light_error,
    onError = astraea_light_onError,
    errorContainer = astraea_light_errorContainer,
    onErrorContainer = astraea_light_onErrorContainer,
    background = astraea_light_background,
    onBackground = astraea_light_onBackground,
    surface = astraea_light_surface,
    onSurface = astraea_light_onSurface,
    surfaceVariant = astraea_light_surfaceVariant,
    onSurfaceVariant = astraea_light_onSurfaceVariant,
    outline = astraea_light_outline,
    outlineVariant = astraea_light_outlineVariant,
    inverseSurface = astraea_light_inverseSurface,
    inverseOnSurface = astraea_light_inverseOnSurface,
    inversePrimary = astraea_light_inversePrimary,
    surfaceTint = astraea_light_surfaceTint,
    scrim = astraea_light_scrim,
    surfaceContainerLowest = astraea_light_surfaceContainerLowest,
    surfaceContainerLow = astraea_light_surfaceContainerLow,
    surfaceContainer = astraea_light_surfaceContainer,
    surfaceContainerHigh = astraea_light_surfaceContainerHigh,
    surfaceContainerHighest = astraea_light_surfaceContainerHighest,
)
