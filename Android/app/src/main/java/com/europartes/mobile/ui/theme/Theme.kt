package com.europartes.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Roles the web design system has and Material's color scheme does not. */
@Immutable
data class EuropartesExtendedColors(
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val muted: Color,
    val raised: Color,
    val sidebar: Color,
    val sidebarBorder: Color,
    val sidebarHover: Color,
    val sidebarActive: Color,
    val sidebarText: Color,
    val sidebarTextSoft: Color,
    val sidebarLabel: Color,
    val accent: Color,
    val chart: List<Color>,
)

private val LightExtended = EuropartesExtendedColors(
    success = EuropartesSuccess,
    successSoft = EuropartesSuccessContainer,
    warning = EuropartesWarning,
    warningSoft = EuropartesWarningContainer,
    info = EuropartesBlue,
    infoSoft = EuropartesBlueSoft,
    muted = EuropartesMuted,
    raised = EuropartesSurface,
    sidebar = EuropartesBlue,
    sidebarBorder = Color(0xFF1949A0),
    sidebarHover = Color(0xFF17469F),
    sidebarActive = EuropartesYellow.copy(alpha = 0.13f),
    sidebarText = Color.White,
    sidebarTextSoft = Color(0xFFD4DEF1),
    sidebarLabel = Color(0xFFB6C6E4),
    accent = EuropartesYellow,
    chart = listOf(EuropartesBlue, Color(0xFFE0A800), Color(0xFF0F7D57), Color(0xFF6B8FD6), Color(0xFFB42318)),
)

private val DarkExtended = EuropartesExtendedColors(
    success = Color(0xFF34D39A),
    successSoft = Color(0xFF34D39A).copy(alpha = 0.14f),
    warning = Color(0xFFFFDF5A),
    warningSoft = EuropartesYellow.copy(alpha = 0.14f),
    info = EuropartesDarkPrimary,
    infoSoft = Color(0xFF6B94EE).copy(alpha = 0.16f),
    muted = EuropartesDarkMuted,
    raised = EuropartesDarkRaised,
    sidebar = Color(0xFF071A43),
    sidebarBorder = Color(0xFF17366F),
    sidebarHover = Color(0xFF10295A),
    sidebarActive = EuropartesYellow.copy(alpha = 0.13f),
    sidebarText = Color(0xFFF6F8FC),
    sidebarTextSoft = Color(0xFFBDC9DC),
    sidebarLabel = Color(0xFF9BAECC),
    accent = EuropartesYellow,
    chart = listOf(EuropartesDarkPrimary, EuropartesYellow, Color(0xFF34D39A), Color(0xFFC39AFF), Color(0xFFFF8295)),
)

private val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

/** Access to [EuropartesExtendedColors] for the active light or dark theme. */
object EuropartesTheme {
    val colors: EuropartesExtendedColors
        @Composable get() = LocalExtendedColors.current
}

private val LightColorScheme = lightColorScheme(
    primary = EuropartesBlue,
    onPrimary = EuropartesSurface,
    primaryContainer = EuropartesBlueSoft,
    onPrimaryContainer = EuropartesBlue,
    inversePrimary = EuropartesDarkPrimary,
    secondary = EuropartesYellow,
    onSecondary = EuropartesYellowInk,
    secondaryContainer = EuropartesWarningContainer,
    onSecondaryContainer = EuropartesYellowInk,
    tertiary = EuropartesSuccess,
    onTertiary = EuropartesSurface,
    tertiaryContainer = EuropartesSuccessContainer,
    onTertiaryContainer = EuropartesSuccess,
    background = EuropartesCanvas,
    onBackground = EuropartesInk,
    surface = EuropartesSurface,
    onSurface = EuropartesInk,
    surfaceVariant = EuropartesSurfaceMuted,
    onSurfaceVariant = EuropartesSecondaryText,
    surfaceTint = Color.Transparent,
    surfaceBright = EuropartesSurface,
    surfaceDim = EuropartesCanvas,
    surfaceContainerLowest = EuropartesSurface,
    surfaceContainerLow = EuropartesSurface,
    surfaceContainer = EuropartesSurface,
    surfaceContainerHigh = EuropartesSurface,
    surfaceContainerHighest = EuropartesSurfaceMuted,
    outline = EuropartesBorder,
    outlineVariant = EuropartesBorderStrong,
    error = EuropartesError,
    onError = EuropartesSurface,
    errorContainer = EuropartesErrorContainer,
    onErrorContainer = EuropartesError,
    scrim = Color(0x8C0B1524),
)

private val ColorForDarkError = Color(0xFFFF6B6F)

private val DarkColorScheme = darkColorScheme(
    primary = EuropartesDarkPrimary,
    onPrimary = Color(0xFF091D46),
    primaryContainer = Color(0xFF6B94EE).copy(alpha = 0.16f),
    onPrimaryContainer = EuropartesDarkPrimaryHover,
    inversePrimary = EuropartesBlue,
    secondary = EuropartesYellow,
    onSecondary = Color(0xFF3D3000),
    secondaryContainer = EuropartesYellow.copy(alpha = 0.14f),
    onSecondaryContainer = Color(0xFFFFDF5A),
    tertiary = Color(0xFF34D39A),
    onTertiary = EuropartesDarkBackground,
    tertiaryContainer = Color(0xFF34D39A).copy(alpha = 0.14f),
    onTertiaryContainer = Color(0xFF34D39A),
    background = EuropartesDarkBackground,
    onBackground = EuropartesDarkInk,
    surface = EuropartesDarkSurface,
    onSurface = EuropartesDarkInk,
    surfaceVariant = EuropartesDarkSurfaceMuted,
    onSurfaceVariant = EuropartesDarkSecondaryText,
    surfaceTint = Color.Transparent,
    surfaceBright = EuropartesDarkRaised,
    surfaceDim = EuropartesDarkBackground,
    surfaceContainerLowest = EuropartesDarkBackground,
    surfaceContainerLow = EuropartesDarkSurface,
    surfaceContainer = EuropartesDarkSurface,
    surfaceContainerHigh = EuropartesDarkRaised,
    surfaceContainerHighest = EuropartesDarkRaised,
    outline = EuropartesDarkBorder,
    outlineVariant = EuropartesDarkBorderStrong,
    error = ColorForDarkError,
    onError = EuropartesDarkBackground,
    errorContainer = Color(0xFF48252B),
    onErrorContainer = ColorForDarkError,
    scrim = Color(0xB3050A12),
)

// Same geometry as brand.css: controls 7, cards 10, modals 12.
private val EuropartesShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(7.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

@Composable
fun EuropartesMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Brand color roles remain stable rather than changing with wallpaper color.
    CompositionLocalProvider(LocalExtendedColors provides if (darkTheme) DarkExtended else LightExtended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = EuropartesShapes,
            content = content,
        )
    }
}
