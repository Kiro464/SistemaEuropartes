package com.europartes.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

private val EuropartesSans = FontFamily.SansSerif

private fun style(size: TextUnit, weight: FontWeight, lineHeight: TextUnit, tracking: TextUnit = TextUnit.Unspecified) =
    TextStyle(
        fontFamily = EuropartesSans,
        fontSize = size,
        fontWeight = weight,
        lineHeight = lineHeight,
        letterSpacing = tracking,
    )

val Typography = Typography(
    displaySmall = style(36.sp, FontWeight.Bold, 42.sp, (-0.7).sp),
    headlineLarge = style(30.sp, FontWeight.Bold, 36.sp, (-0.45).sp),
    headlineMedium = style(25.sp, FontWeight.Bold, 31.sp, (-0.3).sp),
    headlineSmall = style(22.sp, FontWeight.SemiBold, 28.sp),
    titleLarge = style(20.sp, FontWeight.SemiBold, 26.sp),
    titleMedium = style(16.sp, FontWeight.SemiBold, 22.sp),
    titleSmall = style(14.sp, FontWeight.SemiBold, 20.sp),
    bodyLarge = style(16.sp, FontWeight.Normal, 24.sp),
    bodyMedium = style(14.sp, FontWeight.Normal, 21.sp),
    bodySmall = style(12.sp, FontWeight.Normal, 17.sp),
    labelLarge = style(14.sp, FontWeight.SemiBold, 20.sp),
    labelMedium = style(12.sp, FontWeight.Medium, 16.sp),
    labelSmall = style(11.sp, FontWeight.Medium, 15.sp),
)
