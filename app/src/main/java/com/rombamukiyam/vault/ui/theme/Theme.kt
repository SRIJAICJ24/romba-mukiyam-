package com.rombamukiyam.vault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = VioletPale,
    onPrimaryContainer = Navy,
    secondary = Violet,
    onSecondary = Color.White,
    secondaryContainer = VioletPale,
    onSecondaryContainer = Indigo,
    tertiary = SafeGreen,
    onTertiary = Color.White,
    background = SurfaceLight,
    onBackground = OnLight,
    surface = Color.White,
    onSurface = OnLight,
    surfaceVariant = SurfaceLightVariant,
    onSurfaceVariant = GreyText,
    outline = Color(0xFFC3C6D6),
    error = CriticalRed,
    onError = Color.White,
    errorContainer = CriticalRedContainer,
    onErrorContainer = Color(0xFF6E1B1B),
)

private val DarkColors = darkColorScheme(
    primary = VioletLight,
    onPrimary = NavyDeep,
    primaryContainer = IndigoSoft,
    onPrimaryContainer = OnDark,
    secondary = VioletLight,
    onSecondary = NavyDeep,
    secondaryContainer = Indigo,
    onSecondaryContainer = OnDark,
    tertiary = SafeGreen,
    onTertiary = Color.White,
    background = SurfaceDark,
    onBackground = OnDark,
    surface = SurfaceDarkVariant,
    onSurface = OnDark,
    surfaceVariant = Color(0xFF262D52),
    onSurfaceVariant = GreyTextDark,
    outline = Color(0xFF4A5075),
    error = Color(0xFFF08A8A),
    onError = Color(0xFF4A0E0E),
    errorContainer = Color(0xFF6E1B1B),
    onErrorContainer = CriticalRedContainer,
)

/** Semantic colours that Material3 does not model: safe / attention / critical. */
@Immutable
data class SemanticColors(
    val safe: Color,
    val safeContainer: Color,
    val onSafeContainer: Color,
    val attention: Color,
    val attentionContainer: Color,
    val onAttentionContainer: Color,
    val critical: Color,
    val criticalContainer: Color,
    val onCriticalContainer: Color,
)

private val LightSemantic = SemanticColors(
    safe = SafeGreen,
    safeContainer = SafeGreenContainer,
    onSafeContainer = Color(0xFF0F4D31),
    attention = AttentionAmber,
    attentionContainer = AttentionAmberContainer,
    onAttentionContainer = Color(0xFF5C3A00),
    critical = CriticalRed,
    criticalContainer = CriticalRedContainer,
    onCriticalContainer = Color(0xFF6E1B1B),
)

private val DarkSemantic = SemanticColors(
    safe = Color(0xFF5FD39B),
    safeContainer = Color(0xFF14432E),
    onSafeContainer = Color(0xFFCFF2DF),
    attention = Color(0xFFF2B75A),
    attentionContainer = Color(0xFF4F3400),
    onAttentionContainer = Color(0xFFFFE4B8),
    critical = Color(0xFFF08A8A),
    criticalContainer = Color(0xFF5A1717),
    onCriticalContainer = Color(0xFFFBE1E1),
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemantic }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun RombaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // In-app text size multiplies the system font scale so accessibility settings are respected.
    val base = LocalDensity.current
    val density = Density(base.density, base.fontScale * textScale)
    CompositionLocalProvider(
        LocalSemanticColors provides if (dark) DarkSemantic else LightSemantic,
        LocalDensity provides density,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = RombaTypography,
            content = content,
        )
    }
}

object RombaColors {
    val semantic: SemanticColors
        @Composable get() = LocalSemanticColors.current
}
