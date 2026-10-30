package com.europartes.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = EuropartesBlue,
    onPrimary = EuropartesSurface,
    primaryContainer = EuropartesBlueSoft,
    onPrimaryContainer = EuropartesBlue,
    secondary = EuropartesYellow,
    onSecondary = EuropartesYellowInk,
    secondaryContainer = EuropartesWarningContainer,
    onSecondaryContainer = EuropartesYellowInk,
    tertiary = EuropartesSuccess,
    onTertiary = EuropartesSurface,
    background = EuropartesCanvas,
    onBackground = EuropartesInk,
    surface = EuropartesSurface,
    onSurface = EuropartesInk,
    surfaceVariant = EuropartesSurfaceMuted,
    onSurfaceVariant = EuropartesSecondaryText,
    outline = EuropartesBorder,
    outlineVariant = EuropartesBorderStrong,
    error = EuropartesError,
    onError = EuropartesSurface,
    errorContainer = EuropartesErrorContainer,
    onErrorContainer = EuropartesError,
)

private val ColorForDarkAccent = androidx.compose.ui.graphics.Color(0xFFFFDF5A)
private val ColorForDarkSuccess = androidx.compose.ui.graphics.Color(0xFF34D39A)
private val ColorForDarkError = androidx.compose.ui.graphics.Color(0xFFFF6B6F)
private val ColorForDarkErrorContainer = androidx.compose.ui.graphics.Color(0xFF48252B)

private val DarkColorScheme = darkColorScheme(
    primary = EuropartesDarkPrimary,
    onPrimary = EuropartesDarkBackground,
    primaryContainer = EuropartesDarkSurfaceMuted,
    onPrimaryContainer = EuropartesDarkPrimary,
    secondary = EuropartesYellow,
    onSecondary = EuropartesYellowInk,
    secondaryContainer = EuropartesYellow.copy(alpha = 0.16f),
    onSecondaryContainer = ColorForDarkAccent,
    tertiary = ColorForDarkSuccess,
    onTertiary = EuropartesDarkBackground,
    background = EuropartesDarkBackground,
    onBackground = EuropartesDarkInk,
    surface = EuropartesDarkSurface,
    onSurface = EuropartesDarkInk,
    surfaceVariant = EuropartesDarkSurfaceMuted,
    onSurfaceVariant = EuropartesDarkSecondaryText,
    outline = EuropartesDarkBorder,
    outlineVariant = EuropartesDarkBorderStrong,
    error = ColorForDarkError,
    onError = EuropartesDarkBackground,
    errorContainer = ColorForDarkErrorContainer,
    onErrorContainer = ColorForDarkError,
)

@Composable
fun EuropartesMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Brand color roles remain stable rather than changing with wallpaper color.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
