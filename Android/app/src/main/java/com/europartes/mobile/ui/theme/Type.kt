package com.europartes.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.europartes.mobile.R

// Same family as the web. Bundled because the system font depends on the device: some phones let
// the user pick decorative fonts, whose widths would break tables and money columns.
private val EuropartesSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

private fun style(size: TextUnit, weight: FontWeight, lineHeight: TextUnit, tracking: TextUnit = 0.sp) =
    TextStyle(
        fontFamily = EuropartesSans,
        fontSize = size,
        fontWeight = weight,
        lineHeight = lineHeight,
        letterSpacing = tracking,
    )

// Mirrors brand.css: small labels instead of uppercase, weight 600 titles with tight tracking.
val Typography = Typography(
    displaySmall = style(32.sp, FontWeight.SemiBold, 38.sp, (-0.6).sp),
    headlineLarge = style(28.sp, FontWeight.SemiBold, 34.sp, (-0.5).sp),
    headlineMedium = style(23.sp, FontWeight.SemiBold, 29.sp, (-0.35).sp),
    headlineSmall = style(20.sp, FontWeight.SemiBold, 26.sp, (-0.25).sp),
    titleLarge = style(18.sp, FontWeight.SemiBold, 24.sp, (-0.2).sp),
    titleMedium = style(15.sp, FontWeight.SemiBold, 21.sp, (-0.1).sp),
    titleSmall = style(14.sp, FontWeight.SemiBold, 20.sp),
    bodyLarge = style(15.sp, FontWeight.Normal, 23.sp),
    bodyMedium = style(14.sp, FontWeight.Normal, 21.sp),
    bodySmall = style(12.sp, FontWeight.Normal, 17.sp),
    labelLarge = style(14.sp, FontWeight.Medium, 20.sp),
    labelMedium = style(13.sp, FontWeight.Medium, 18.sp),
    labelSmall = style(12.sp, FontWeight.Medium, 16.sp),
)

/** Figures line up in columns of money and quantities, like `tabular-nums` on the web. */
val TextStyle.tabular: TextStyle get() = copy(fontFeatureSettings = "tnum")
