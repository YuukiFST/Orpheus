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
    onPrimary = VantaBlack,
    primaryContainer = VantaGray0,
    onPrimaryContainer = VantaAccent,
    secondary = VantaGray2,
    onSecondary = VantaWhite,
    secondaryContainer = VantaGray0,
    onSecondaryContainer = VantaGray4,
    tertiary = VantaGray3,
    onTertiary = VantaBlack,
    background = VantaBlack,
    onBackground = VantaWhite,
    surface = VantaBlack,
    onSurface = VantaWhite,
    surfaceVariant = VantaGray0,
    onSurfaceVariant = VantaGray2,
    outline = VantaGray1,
    outlineVariant = VantaGray0,
    surfaceTint = VantaAccent,
    error = VantaHazard,
    onError = VantaBlack,
    surfaceContainerLowest = VantaBlack,
    surfaceContainerLow = VantaGray0,
    surfaceContainer = VantaGray0,
    surfaceContainerHigh = VantaGray1,
    surfaceContainerHighest = Color(0xFF3A3A3A),
)

val LightColorScheme = lightColorScheme(
    primary = VantaGray0,
    onPrimary = VantaWhite,
    primaryContainer = VantaGray4,
    onPrimaryContainer = VantaBlack,
    secondary = VantaAccent,
    onSecondary = VantaWhite,
    secondaryContainer = VantaGray4,
    onSecondaryContainer = VantaGray0,
    tertiary = VantaGray1,
    onTertiary = VantaWhite,
    background = VantaWhite,
    onBackground = VantaBlack,
    surface = VantaWhite,
    onSurface = VantaBlack,
    surfaceVariant = VantaGray4,
    onSurfaceVariant = VantaGray1,
    outline = VantaAccent,
    outlineVariant = VantaGray4,
    surfaceTint = VantaGray0,
    error = Color(0xFF5C5C5C),
    onError = VantaWhite,
    surfaceContainerLowest = VantaWhite,
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = VantaGray4,
    surfaceContainerHigh = Color(0xFFE0E0E0),
    surfaceContainerHighest = Color(0xFFD0D0D0),
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
