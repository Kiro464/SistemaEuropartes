package com.europartes.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ColorPlaceholderPrimary = androidx.compose.ui.graphics.Color(0xFF8DD8DD)

private val LightColorScheme = lightColorScheme(
    primary = EuropartesNavy,
    onPrimary = EuropartesSurface,
    primaryContainer = EuropartesTealContainer,
    onPrimaryContainer = EuropartesNavy,
    secondary = EuropartesTeal,
    onSecondary = EuropartesSurface,
    background = EuropartesCanvas,
    onBackground = EuropartesInk,
    surface = EuropartesSurface,
    onSurface = EuropartesInk,
    surfaceVariant = EuropartesCanvas,
    onSurfaceVariant = EuropartesMuted,
    outline = EuropartesBorder,
    error = EuropartesError,
    onError = EuropartesSurface,
    errorContainer = EuropartesErrorContainer,
    onErrorContainer = EuropartesError,
)

private val DarkColorScheme = darkColorScheme(
    primary = ColorPlaceholderPrimary,
    onPrimary = EuropartesDarkBackground,
    secondary = EuropartesTeal,
    background = EuropartesDarkBackground,
    onBackground = EuropartesDarkText,
    surface = EuropartesDarkSurface,
    onSurface = EuropartesDarkText,
    outline = EuropartesMuted,
    error = EuropartesError,
)

@Composable
fun EuropartesMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Se desactiva el color dinámico para que las evidencias sean visualmente consistentes.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme && !dynamicColor) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
