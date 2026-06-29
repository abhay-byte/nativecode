package com.ivarna.nativecode.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// =============================================================================
// Obsidian Nexus Dark Color Scheme
// =============================================================================
val ObsidianDarkColorScheme = darkColorScheme(
    primary = ObsidianPrimary,
    onPrimary = ObsidianOnPrimary,
    primaryContainer = ObsidianPrimaryContainer,
    onPrimaryContainer = ObsidianOnPrimaryContainer,
    inversePrimary = ObsidianInversePrimary,
    secondary = ObsidianSecondary,
    onSecondary = ObsidianOnSecondary,
    secondaryContainer = ObsidianSecondaryContainer,
    onSecondaryContainer = ObsidianOnSecondaryContainer,
    tertiary = ObsidianTertiary,
    onTertiary = ObsidianOnTertiary,
    tertiaryContainer = ObsidianTertiaryContainer,
    onTertiaryContainer = ObsidianOnTertiaryContainer,
    error = ObsidianError,
    onError = ObsidianOnError,
    errorContainer = ObsidianErrorContainer,
    onErrorContainer = ObsidianOnErrorContainer,
    background = ObsidianBackground,
    onBackground = ObsidianOnBackground,
    surface = ObsidianSurface,
    onSurface = ObsidianOnSurface,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianOnSurfaceVariant,
    surfaceTint = ObsidianSurfaceTint,
    inverseSurface = ObsidianInverseSurface,
    inverseOnSurface = ObsidianInverseOnSurface,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    surfaceBright = ObsidianSurfaceBright,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceContainerHigh,
    surfaceContainerHighest = ObsidianSurfaceContainerHighest,
    surfaceContainerLow = ObsidianSurfaceContainerLow,
    surfaceContainerLowest = ObsidianSurfaceContainerLowest,
    surfaceDim = ObsidianSurfaceDim,
    scrim = Color.Black
)

// =============================================================================
// Obsidian Nexus Light Color Scheme
// =============================================================================
val ObsidianLightColorScheme = lightColorScheme(
    primary = ObsidianLightPrimary,
    onPrimary = ObsidianLightOnPrimary,
    primaryContainer = ObsidianLightPrimaryContainer,
    onPrimaryContainer = ObsidianLightOnPrimaryContainer,
    inversePrimary = ObsidianLightInversePrimary,
    secondary = ObsidianLightSecondary,
    onSecondary = ObsidianLightOnSecondary,
    secondaryContainer = ObsidianLightSecondaryContainer,
    onSecondaryContainer = ObsidianLightOnSecondaryContainer,
    tertiary = ObsidianLightTertiary,
    onTertiary = ObsidianLightOnTertiary,
    tertiaryContainer = ObsidianLightTertiaryContainer,
    onTertiaryContainer = ObsidianLightOnTertiaryContainer,
    error = ObsidianLightError,
    onError = ObsidianLightOnError,
    errorContainer = ObsidianLightErrorContainer,
    onErrorContainer = ObsidianLightOnErrorContainer,
    background = ObsidianLightBackground,
    onBackground = ObsidianLightOnBackground,
    surface = ObsidianLightSurface,
    onSurface = ObsidianLightOnSurface,
    surfaceVariant = ObsidianLightSurfaceVariant,
    onSurfaceVariant = ObsidianLightOnSurfaceVariant,
    surfaceTint = ObsidianLightPrimary,
    inverseSurface = ObsidianLightInverseSurface,
    inverseOnSurface = ObsidianLightInverseOnSurface,
    outline = ObsidianLightOutline,
    outlineVariant = ObsidianLightOutlineVariant,
    surfaceBright = ObsidianLightSurfaceBright,
    surfaceContainer = ObsidianLightSurfaceContainer,
    surfaceContainerHigh = ObsidianLightSurfaceContainerHigh,
    surfaceContainerHighest = ObsidianLightSurfaceContainerHighest,
    surfaceContainerLow = ObsidianLightSurfaceContainerLow,
    surfaceContainerLowest = ObsidianLightSurfaceContainerLowest,
    surfaceDim = ObsidianLightSurfaceDim,
    scrim = Color.Black
)

@Composable
fun NativeCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic color is disabled in Obsidian Nexus to preserve brand identity
        darkTheme -> ObsidianDarkColorScheme
        else -> ObsidianLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
