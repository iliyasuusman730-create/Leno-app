package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = LenoPrimary,
    onPrimary = LenoWhite,
    primaryContainer = LenoPrimaryDark,
    onPrimaryContainer = LenoPrimaryLight,
    inversePrimary = LenoPrimaryLight,
    secondary = LenoPrimaryLight,
    onSecondary = SurfaceDark,
    secondaryContainer = Color(0xFF143023),
    onSecondaryContainer = LenoPrimaryLight,
    tertiary = LenoInfo,
    onTertiary = LenoWhite,
    tertiaryContainer = Color(0xFF1E3A8A),
    onTertiaryContainer = LenoInfoBg,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
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
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = LenoModalBackdrop
)

private val LightColorScheme = lightColorScheme(
    primary = LenoPrimary,
    onPrimary = LenoWhite,
    primaryContainer = LenoPrimaryLight,
    onPrimaryContainer = LenoPrimaryDark,
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
