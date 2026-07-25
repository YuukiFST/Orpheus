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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.yuukifst.orpheus.presentation.viewmodel.ColorSchemePair
import androidx.core.graphics.ColorUtils

val LocalOrpheusDarkTheme = staticCompositionLocalOf { false }

/** Hard terminal borders only for Orpheus Dark/Light — off in Pixel (PixelPlayer parity). */
val LocalTerminalChrome = staticCompositionLocalOf { false }

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
    primary = MonoWhite,
    onPrimary = MonoBlack,
    primaryContainer = MonoBlack,
    onPrimaryContainer = MonoWhite,
    secondary = MonoWhite,
    onSecondary = MonoBlack,
    secondaryContainer = MonoBlack,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoWhite,
    onTertiary = MonoBlack,
    tertiaryContainer = MonoBlack,
    onTertiaryContainer = MonoWhite,
    background = MonoBlack,
    onBackground = MonoWhite,
    surface = MonoBlack,
    onSurface = MonoWhite,
    surfaceVariant = MonoBlack,
    onSurfaceVariant = MonoWhite,
    outline = MonoWhite,
    outlineVariant = MonoWhite.copy(alpha = 0.5f),
    surfaceTint = Color.Transparent,
    error = VantaHazard,
    onError = MonoBlack,
    surfaceContainerLowest = MonoBlack,
    surfaceContainerLow = MonoBlack,
    surfaceContainer = MonoBlack,
    surfaceContainerHigh = MonoBlack,
    surfaceContainerHighest = MonoBlack,
    primaryFixed = MonoWhite,
    onPrimaryFixed = MonoBlack,
    primaryFixedDim = MonoWhite.copy(alpha = 0.7f),
    onPrimaryFixedVariant = MonoWhite,
    secondaryFixed = MonoWhite,
    onSecondaryFixed = MonoBlack,
    secondaryFixedDim = MonoWhite.copy(alpha = 0.7f),
    onSecondaryFixedVariant = MonoWhite,
    tertiaryFixed = MonoWhite,
    onTertiaryFixed = MonoBlack,
    tertiaryFixedDim = MonoWhite.copy(alpha = 0.7f),
    onTertiaryFixedVariant = MonoWhite,
)

val LightColorScheme = lightColorScheme(
    primary = MonoBlack,
    onPrimary = MonoWhite,
    primaryContainer = MonoWhite,
    onPrimaryContainer = MonoBlack,
    secondary = MonoBlack,
    onSecondary = MonoWhite,
    secondaryContainer = MonoWhite,
    onSecondaryContainer = MonoBlack,
    tertiary = MonoBlack,
    onTertiary = MonoWhite,
    tertiaryContainer = MonoWhite,
    onTertiaryContainer = MonoBlack,
    background = MonoWhite,
    onBackground = MonoBlack,
    surface = MonoWhite,
    onSurface = MonoBlack,
    surfaceVariant = MonoWhite,
    onSurfaceVariant = MonoBlack,
    outline = MonoBlack,
    outlineVariant = MonoBlack.copy(alpha = 0.5f),
    surfaceTint = Color.Transparent,
    error = Color(0xFFBA1A1A),
    onError = MonoWhite,
    surfaceContainerLowest = MonoWhite,
    surfaceContainerLow = MonoWhite,
    surfaceContainer = MonoWhite,
    surfaceContainerHigh = MonoWhite,
    surfaceContainerHighest = MonoWhite,
    primaryFixed = MonoBlack,
    onPrimaryFixed = MonoWhite,
    primaryFixedDim = MonoBlack.copy(alpha = 0.7f),
    onPrimaryFixedVariant = MonoBlack,
    secondaryFixed = MonoBlack,
    onSecondaryFixed = MonoWhite,
    secondaryFixedDim = MonoBlack.copy(alpha = 0.7f),
    onSecondaryFixedVariant = MonoBlack,
    tertiaryFixed = MonoBlack,
    onTertiaryFixed = MonoWhite,
    tertiaryFixedDim = MonoBlack.copy(alpha = 0.7f),
    onTertiaryFixedVariant = MonoBlack,
)

// PixelPlayerOSS dark seed + filled surface/container roles so list/buttons match PP look
// (upstream leaves most roles to Material defaults / dynamic; static Pixel needs explicit fills)
private val PixelSurfaceLow = Color(0xFF24183A)
private val PixelSurfaceHigh = Color(0xFF34284A)
private val PixelSurfaceHighest = Color(0xFF3E3254)
private val PixelPrimaryContainer = Color(0xFF5C2D6B)
private val PixelSecondaryContainer = Color(0xFF6B2F4A)
private val PixelTertiaryContainer = Color(0xFF6B3A2A)
private val PixelOutline = Color(0xFF8A7A9A)
private val PixelSurfaceVariant = Color(0xFF3A2F50)

val PixelColorScheme = darkColorScheme(
    primary = PixelPrimary,
    onPrimary = PixelOnPrimary,
    primaryContainer = PixelPrimaryContainer,
    onPrimaryContainer = PixelOnSurface,
    secondary = PixelSecondary,
    onSecondary = PixelOnSecondary,
    secondaryContainer = PixelSecondaryContainer,
    onSecondaryContainer = PixelOnSurface,
    tertiary = PixelTertiary,
    onTertiary = PixelOnTertiary,
    tertiaryContainer = PixelTertiaryContainer,
    onTertiaryContainer = PixelOnSurface,
    background = PixelBackground,
    onBackground = PixelOnBackground,
    surface = PixelSurface,
    onSurface = PixelOnSurface,
    surfaceVariant = PixelSurfaceVariant,
    onSurfaceVariant = PixelOnSurface.copy(alpha = 0.85f),
    outline = PixelOutline,
    outlineVariant = PixelOutline.copy(alpha = 0.5f),
    error = PixelError,
    onError = PixelOnPrimary,
    surfaceContainerLowest = PixelBackground,
    surfaceContainerLow = PixelSurfaceLow,
    surfaceContainer = PixelSurface,
    surfaceContainerHigh = PixelSurfaceHigh,
    surfaceContainerHighest = PixelSurfaceHighest,
    surfaceTint = PixelPrimary,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrpheusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useSmoothCorners: Boolean = true,
    scheme: AppThemeScheme = AppThemeScheme.LIGHT,
    colorSchemePairOverride: ColorSchemePair? = null,
    content: @Composable () -> Unit
) {
    val isPixel = scheme == AppThemeScheme.PIXEL
    val finalColorScheme = when {
        colorSchemePairOverride != null -> {
            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
        }
        isPixel -> PixelColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val shapeSet = remember(isPixel, useSmoothCorners) {
        when {
            isPixel -> OrpheusShapeSets.Pixel
            useSmoothCorners -> OrpheusShapeSets.Rounded
            else -> OrpheusShapeSets.Square
        }
    }
    OrpheusActiveShapes.set = shapeSet
    val materialShapes = remember(isPixel, shapeSet) {
        if (isPixel) PixelPlayerMaterialShapes else orpheusMaterialShapes(shapeSet)
    }
    val typography = if (isPixel) Typography else TerminalTypography

    OrpheusStatusBarStyle(
        color = finalColorScheme.background,
        navigationColor = finalColorScheme.background
    )

    CompositionLocalProvider(
        LocalOrpheusDarkTheme provides darkTheme,
        LocalOrpheusShapes provides shapeSet,
        LocalTerminalChrome provides !isPixel,
    ) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = typography,
            shapes = materialShapes,
            content = content
        )
    }
}
