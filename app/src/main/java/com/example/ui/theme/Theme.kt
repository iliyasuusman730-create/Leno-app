package com.example.ui.theme

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val LocalLenoDarkTheme = staticCompositionLocalOf { false }

val LenoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = LenoPrimary,
    onPrimary = LenoWhite,
    primaryContainer = Color(0xFF134030),
    onPrimaryContainer = Color(0xFFC6F6E2),
    inversePrimary = LenoPrimaryLight,
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF06291D),
    secondaryContainer = Color(0xFF143023),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFF60A5FA),
    onTertiary = Color(0xFF0A192F),
    tertiaryContainer = Color(0xFF1E3A8A),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = Color(0xFF161F1B),
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = LenoPrimary,
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = BackgroundDark,
    error = LenoError,
    onError = LenoWhite,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    outline = Color(0xFF2D3C35),
    outlineVariant = Color(0xFF222E28),
    scrim = LenoModalBackdrop
)

private val LightColorScheme = lightColorScheme(
    primary = LenoPrimary,
    onPrimary = LenoWhite,
    primaryContainer = LenoPrimaryLight,
    onPrimaryContainer = LenoBlack,
    inversePrimary = LenoPrimaryLight,
    secondary = LenoPrimaryDark,
    onSecondary = LenoWhite,
    secondaryContainer = LenoPrimaryVeryLight,
    onSecondaryContainer = LenoTextPrimary,
    tertiary = LenoInfo,
    onTertiary = LenoWhite,
    tertiaryContainer = LenoInfoBg,
    onTertiaryContainer = LenoInfo,
    background = LenoMainBackground,
    onBackground = LenoTextPrimary,
    surface = LenoSurface,
    onSurface = LenoTextPrimary,
    surfaceVariant = LenoSurfaceSecondary,
    onSurfaceVariant = LenoTextSecondary,
    surfaceTint = LenoPrimary,
    inverseSurface = LenoBlack,
    inverseOnSurface = LenoWhite,
    error = LenoError,
    onError = LenoWhite,
    errorContainer = LenoErrorBg,
    onErrorContainer = LenoError,
    outline = LenoBorderDefault,
    outlineVariant = LenoBorderSubtle,
    scrim = LenoModalBackdrop
)

@Composable
fun LenoTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalLenoDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = LenoShapes,
            content = content
        )
    }
}
