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

// Ethereal — Omarchy dark navy palette
private val EtherealSurfaceLow = EtherealColor0
private val EtherealSurfaceHigh = EtherealColor8
private val EtherealSurfaceHighest = Color(0xFF4A5680)
private val EtherealPrimaryContainer = Color(0xFF5A5FA8)
private val EtherealSecondaryContainer = Color(0xFF6B8578)
private val EtherealTertiaryContainer = Color(0xFF8A7340)
private val EtherealOutline = EtherealColor6
private val EtherealSurfaceVariant = EtherealColor0

val EtherealColorScheme = darkColorScheme(
    primary = EtherealAccent,
    onPrimary = EtherealBackground,
    primaryContainer = EtherealPrimaryContainer,
    onPrimaryContainer = EtherealForeground,
    secondary = EtherealColor2,
    onSecondary = EtherealBackground,
    secondaryContainer = EtherealSecondaryContainer,
    onSecondaryContainer = EtherealForeground,
    tertiary = EtherealColor3,
    onTertiary = EtherealBackground,
    tertiaryContainer = EtherealTertiaryContainer,
    onTertiaryContainer = EtherealForeground,
    background = EtherealBackground,
    onBackground = EtherealForeground,
    surface = EtherealColor0,
    onSurface = EtherealForeground,
    surfaceVariant = EtherealSurfaceVariant,
    onSurfaceVariant = EtherealForeground.copy(alpha = 0.85f),
    outline = EtherealOutline,
    outlineVariant = EtherealOutline.copy(alpha = 0.5f),
    error = EtherealColor1,
    onError = EtherealBackground,
    surfaceContainerLowest = EtherealBackground,
    surfaceContainerLow = EtherealSurfaceLow,
    surfaceContainer = EtherealColor0,
    surfaceContainerHigh = EtherealSurfaceHigh,
    surfaceContainerHighest = EtherealSurfaceHighest,
    surfaceTint = EtherealAccent,
)

// Rose Pine — Omarchy light cream palette
private val RosePineSurfaceLow = RosePineColor0
private val RosePineSurfaceHigh = Color(0xFFE8DFD5)
private val RosePineSurfaceHighest = Color(0xFFDFD5C9)
private val RosePinePrimaryContainer = Color(0xFFB8D4D8)
private val RosePineSecondaryContainer = Color(0xFFB8C8D4)
private val RosePineTertiaryContainer = Color(0xFFF0D4A8)
private val RosePineOutline = RosePineColor8
private val RosePineSurfaceVariant = RosePineColor0

val RosePineColorScheme = lightColorScheme(
    primary = RosePineAccent,
    onPrimary = RosePineBackground,
    primaryContainer = RosePinePrimaryContainer,
    onPrimaryContainer = RosePineForeground,
    secondary = RosePineColor2,
    onSecondary = RosePineBackground,
    secondaryContainer = RosePineSecondaryContainer,
    onSecondaryContainer = RosePineForeground,
    tertiary = RosePineColor3,
    onTertiary = RosePineBackground,
    tertiaryContainer = RosePineTertiaryContainer,
    onTertiaryContainer = RosePineForeground,
    background = RosePineBackground,
    onBackground = RosePineForeground,
    surface = RosePineColor0,
    onSurface = RosePineForeground,
    surfaceVariant = RosePineSurfaceVariant,
    onSurfaceVariant = RosePineForeground.copy(alpha = 0.85f),
    outline = RosePineOutline,
    outlineVariant = RosePineOutline.copy(alpha = 0.5f),
    error = RosePineColor1,
    onError = RosePineBackground,
    surfaceContainerLowest = RosePineBackground,
    surfaceContainerLow = RosePineSurfaceLow,
    surfaceContainer = RosePineColor0,
    surfaceContainerHigh = RosePineSurfaceHigh,
    surfaceContainerHighest = RosePineSurfaceHighest,
    surfaceTint = RosePineAccent,
)

// Catppuccin Mocha — Omarchy dark pastel palette
private val CatppuccinMochaSurfaceLow = CatppuccinMochaColor0
private val CatppuccinMochaSurfaceHigh = CatppuccinMochaColor8
private val CatppuccinMochaSurfaceHighest = Color(0xFF6C7086)
private val CatppuccinMochaPrimaryContainer = Color(0xFF7A5A72)
private val CatppuccinMochaSecondaryContainer = Color(0xFF5A7A58)
private val CatppuccinMochaTertiaryContainer = Color(0xFF7A7048)
private val CatppuccinMochaOutline = CatppuccinMochaColor6
private val CatppuccinMochaSurfaceVariant = CatppuccinMochaColor0

val CatppuccinMochaColorScheme = darkColorScheme(
    primary = CatppuccinMochaAccent,
    onPrimary = CatppuccinMochaBackground,
    primaryContainer = CatppuccinMochaPrimaryContainer,
    onPrimaryContainer = CatppuccinMochaForeground,
    secondary = CatppuccinMochaColor2,
    onSecondary = CatppuccinMochaBackground,
    secondaryContainer = CatppuccinMochaSecondaryContainer,
    onSecondaryContainer = CatppuccinMochaForeground,
    tertiary = CatppuccinMochaColor3,
    onTertiary = CatppuccinMochaBackground,
    tertiaryContainer = CatppuccinMochaTertiaryContainer,
    onTertiaryContainer = CatppuccinMochaForeground,
    background = CatppuccinMochaBackground,
    onBackground = CatppuccinMochaForeground,
    surface = CatppuccinMochaColor0,
    onSurface = CatppuccinMochaForeground,
    surfaceVariant = CatppuccinMochaSurfaceVariant,
    onSurfaceVariant = CatppuccinMochaForeground.copy(alpha = 0.85f),
    outline = CatppuccinMochaOutline,
    outlineVariant = CatppuccinMochaOutline.copy(alpha = 0.5f),
    error = CatppuccinMochaColor1,
    onError = CatppuccinMochaBackground,
    surfaceContainerLowest = CatppuccinMochaBackground,
    surfaceContainerLow = CatppuccinMochaSurfaceLow,
    surfaceContainer = CatppuccinMochaColor0,
    surfaceContainerHigh = CatppuccinMochaSurfaceHigh,
    surfaceContainerHighest = CatppuccinMochaSurfaceHighest,
    surfaceTint = CatppuccinMochaAccent,
)

// Sakura — creative pink palette
private val SakuraSurfaceLow = SakuraSurface
private val SakuraSurfaceHighest = Color(0xFFFFC8D6)
private val SakuraSecondaryContainer = Color(0xFFFFD0DC)
private val SakuraTertiaryContainer = Color(0xFFE8A0B0)
private val SakuraSurfaceVariant = SakuraSurface

val SakuraColorScheme = lightColorScheme(
    primary = SakuraPrimary,
    onPrimary = SakuraOnPrimary,
    primaryContainer = SakuraPrimaryContainer,
    onPrimaryContainer = SakuraOnBackground,
    secondary = SakuraSecondary,
    onSecondary = SakuraOnBackground,
    secondaryContainer = SakuraSecondaryContainer,
    onSecondaryContainer = SakuraOnBackground,
    tertiary = SakuraTertiary,
    onTertiary = SakuraOnPrimary,
    tertiaryContainer = SakuraTertiaryContainer,
    onTertiaryContainer = SakuraOnBackground,
    background = SakuraBackground,
    onBackground = SakuraOnBackground,
    surface = SakuraSurface,
    onSurface = SakuraOnBackground,
    surfaceVariant = SakuraSurfaceVariant,
    onSurfaceVariant = SakuraOnBackground.copy(alpha = 0.85f),
    outline = SakuraOutline,
    outlineVariant = SakuraOutline.copy(alpha = 0.5f),
    error = SakuraError,
    onError = SakuraOnPrimary,
    surfaceContainerLowest = SakuraBackground,
    surfaceContainerLow = SakuraSurfaceLow,
    surfaceContainer = SakuraSurface,
    surfaceContainerHigh = SakuraSurfaceHigh,
    surfaceContainerHighest = SakuraSurfaceHighest,
    surfaceTint = SakuraPrimary,
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
    val personality = remember(scheme, useSmoothCorners) { themePersonalityFor(scheme, useSmoothCorners) }
    val isSoftChrome = personality.softChrome
    val finalColorScheme = when {
        colorSchemePairOverride != null -> {
            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
        }
        scheme == AppThemeScheme.PIXEL -> PixelColorScheme
        scheme == AppThemeScheme.ETHEREAL -> EtherealColorScheme
        scheme == AppThemeScheme.ROSE_PINE -> RosePineColorScheme
        scheme == AppThemeScheme.CATPPUCCIN_MOCHA -> CatppuccinMochaColorScheme
        scheme == AppThemeScheme.SAKURA -> SakuraColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val shapeSet = remember(scheme, useSmoothCorners, isSoftChrome) {
        when {
            scheme == AppThemeScheme.SAKURA -> OrpheusShapeSets.Sakura
            scheme == AppThemeScheme.PIXEL || scheme == AppThemeScheme.ETHEREAL || scheme == AppThemeScheme.CATPPUCCIN_MOCHA -> OrpheusShapeSets.Pixel
            scheme == AppThemeScheme.ROSE_PINE -> OrpheusShapeSets.Rounded
            useSmoothCorners -> OrpheusShapeSets.Rounded
            else -> OrpheusShapeSets.Square
        }
    }
    OrpheusActiveShapes.set = shapeSet
    val materialShapes = remember(scheme, shapeSet) {
        if (scheme == AppThemeScheme.PIXEL || scheme == AppThemeScheme.SAKURA ||
            scheme == AppThemeScheme.ETHEREAL || scheme == AppThemeScheme.CATPPUCCIN_MOCHA
        ) {
            if (scheme == AppThemeScheme.PIXEL) PixelPlayerMaterialShapes else orpheusMaterialShapes(shapeSet)
        } else {
            orpheusMaterialShapes(shapeSet)
        }
    }
    val typography = if (isSoftChrome) Typography else TerminalTypography

    OrpheusStatusBarStyle(
        color = finalColorScheme.background,
        navigationColor = finalColorScheme.background
    )

    CompositionLocalProvider(
        LocalOrpheusDarkTheme provides darkTheme,
        LocalOrpheusShapes provides shapeSet,
        LocalTerminalChrome provides !isSoftChrome,
        LocalThemePersonality provides personality,
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
