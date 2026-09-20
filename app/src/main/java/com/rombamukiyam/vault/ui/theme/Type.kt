package com.rombamukiyam.vault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

// Slightly larger than Material defaults: the primary user may be 50+.
val RombaTypography = Typography(
    headlineLarge = style(32, 40, FontWeight.SemiBold),
    headlineMedium = style(28, 36, FontWeight.SemiBold),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(18, 24, FontWeight.Medium),
    titleSmall = style(16, 22, FontWeight.Medium),
    bodyLarge = style(18, 26),
    bodyMedium = style(16, 24),
    bodySmall = style(14, 20),
    labelLarge = style(16, 22, FontWeight.Medium),
    labelMedium = style(14, 18, FontWeight.Medium),
    labelSmall = style(12, 16, FontWeight.Medium),
)
