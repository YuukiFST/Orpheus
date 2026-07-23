package com.yuukifst.orpheus.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.yuukifst.orpheus.presentation.viewmodel.ColorSchemePair
import androidx.core.graphics.ColorUtils

val LocalOrpheusDarkTheme = staticCompositionLocalOf { false }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
@Composable
fun OrpheusStatusBarStyle(
    color: Color,
    useDarkIcons: Boolean = ColorUtils.calculateLuminance(color.toArgb()) > 0.55,
    navigationColor: Color? = null,
    useDarkNavigationIcons: Boolean = navigationColor
        ?.let { ColorUtils.calculateLuminance(it.toArgb()) > 0.55 }
        ?: useDarkIcons
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val updateNavigationBar = navigationColor != null
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = useDarkIcons

            if (updateNavigationBar) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                isAppearanceLightNavigationBars = useDarkNavigationIcons
            }
        }
    }
}

val DarkColorScheme = darkColorScheme(
    primary = VantaAccent,
    onPrimary = Color(0xFF0A1209),
    primaryContainer = VantaPhosphorDim,
    onPrimaryContainer = VantaPhosphorBright,
    secondary = VantaGray3,
    onSecondary = VantaBlack,
    secondaryContainer = VantaGray1,
    onSecondaryContainer = VantaGray4,
    tertiary = VantaGray2,
    onTertiary = VantaWhite,
    tertiaryContainer = Color(0xFF1C2019),
    onTertiaryContainer = VantaGray4,
    background = VantaBlack,
    onBackground = VantaWhite,
    surface = VantaGray0,
    onSurface = VantaWhite,
    surfaceVariant = VantaGray1,
    onSurfaceVariant = VantaGray3,
    outline = VantaAccent.copy(alpha = 0.28f),
    outlineVariant = VantaGray1,
    surfaceTint = VantaAccent.copy(alpha = 0.06f),
    error = VantaHazard,
    onError = VantaBlack,
    surfaceContainerLowest = VantaBlack,
    surfaceContainerLow = Color(0xFF111411),
    surfaceContainer = VantaGray0,
    surfaceContainerHigh = VantaGray1,
    surfaceContainerHighest = Color(0xFF2E332B),
    primaryFixed = VantaPhosphorBright,
    onPrimaryFixed = Color(0xFF0A1209),
    primaryFixedDim = VantaPhosphorDim,
    onPrimaryFixedVariant = VantaGray4,
    secondaryFixed = VantaGray3,
    onSecondaryFixed = VantaBlack,
    secondaryFixedDim = VantaGray2,
    onSecondaryFixedVariant = VantaGray4,
    tertiaryFixed = VantaGray4,
    onTertiaryFixed = VantaBlack,
    tertiaryFixedDim = VantaGray2,
    onTertiaryFixedVariant = VantaGray3,
)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color(0xFFF8FAF6),
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = Color(0xFF3D6B36),
    onSecondary = Color(0xFFF8FAF6),
    secondaryContainer = Color(0xFFE0EDE0),
    onSecondaryContainer = Color(0xFF1A3316),
    tertiary = VantaGray2,
    onTertiary = Color(0xFFF8FAF6),
    tertiaryContainer = LightSurfaceVariant,
    onTertiaryContainer = LightOnSurfaceVariant,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFD0D8CC),
    surfaceTint = LightPrimary.copy(alpha = 0.08f),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F8F3),
    surfaceContainer = Color(0xFFECEFE9),
    surfaceContainerHigh = Color(0xFFE2E8DF),
    surfaceContainerHighest = Color(0xFFD4DCD0),
    primaryFixed = VantaAccent,
    onPrimaryFixed = Color(0xFF0A1F08),
    primaryFixedDim = Color(0xFF3D8A34),
    onPrimaryFixedVariant = Color(0xFF1A3316),
    secondaryFixed = Color(0xFF5BE048),
    onSecondaryFixed = Color(0xFF0A1F08),
    secondaryFixedDim = Color(0xFF3D6B36),
    onSecondaryFixedVariant = Color(0xFF1A3316),
    tertiaryFixed = VantaGray3,
    onTertiaryFixed = LightOnSurface,
    tertiaryFixedDim = VantaGray2,
    onTertiaryFixedVariant = LightOnSurfaceVariant,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrpheusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemePairOverride: ColorSchemePair? = null,
    content: @Composable () -> Unit
) {
    val finalColorScheme = when {
        colorSchemePairOverride != null -> {
            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    OrpheusStatusBarStyle(
        color = finalColorScheme.background,
        navigationColor = finalColorScheme.background
    )

    CompositionLocalProvider(LocalOrpheusDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
