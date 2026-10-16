package com.wifipulse.analyzer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = DarkBackground,
    primaryContainer = ColorTokens.DarkPrimaryContainer,
    onPrimaryContainer = CyanPrimary,
    secondary = CyanSecondary,
    onSecondary = DarkBackground,
    secondaryContainer = ColorTokens.DarkSecondaryContainer,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = BlueAccent,
    onTertiary = DarkBackground,
    tertiaryContainer = ColorTokens.DarkTertiaryContainer,
    onTertiaryContainer = TextPrimaryDark,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = CyanPrimary,
    outline = DarkBorder,
    outlineVariant = Color(0xFF20364B),
    error = Color(0xFFFF6B7D),
    onError = Color(0xFF2A0710),
    errorContainer = Color(0xFF421C2A),
    onErrorContainer = Color(0xFFFFDCE2)
)

private val LightColorScheme = lightColorScheme(
    primary = LightCyanPrimary,
    onPrimary = TextPrimaryLight,
    primaryContainer = ColorTokens.LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF004451),
    secondary = LightCyanSecondary,
    onSecondary = TextPrimaryLight,
    secondaryContainer = ColorTokens.LightSecondaryContainer,
    onSecondaryContainer = Color(0xFF123D49),
    tertiary = LightBlueAccent,
    onTertiary = TextPrimaryLight,
    tertiaryContainer = ColorTokens.LightTertiaryContainer,
    onTertiaryContainer = Color(0xFF30215F),
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = LightCyanPrimary,
    outline = LightBorder,
    outlineVariant = Color(0xFFD4E1EA),
    error = Color(0xFFBA1A35),
    onError = TextPrimaryLight,
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF41000D)
)

private object ColorTokens {
    val DarkPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF10313B)
    val DarkSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF12303A)
    val DarkTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF292544)
    val LightPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD0F7FA)
    val LightSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFD7F1F5)
    val LightTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFEAE4FF)
}

private val CyberpunkShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(21.dp)
)

@Composable
fun WiFiPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Keep a consistent brand palette instead of Android dynamic colors so the UI
    // remains visually coherent across phones and manufacturers.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CyberpunkShapes,
        content = content
    )
}

