package com.yuukifst.orpheus.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMotionRecipe { SLIDE_FADE, SLIDE_SHORT, FADE_LONG, SCALE_FADE }

data class ThemePersonality(
    val softChrome: Boolean,
    val motionRecipe: ThemeMotionRecipe,
    val openDistance: Dp,
)

val LocalThemePersonality = staticCompositionLocalOf {
    ThemePersonality(
        softChrome = false,
        motionRecipe = ThemeMotionRecipe.SLIDE_FADE,
        openDistance = OrpheusMotion.DistanceBase,
    )
}

fun themePersonalityFor(scheme: AppThemeScheme, useSmoothCorners: Boolean): ThemePersonality =
    when (scheme) {
        AppThemeScheme.PIXEL -> ThemePersonality(true, ThemeMotionRecipe.SLIDE_FADE, OrpheusMotion.DistanceBase)
        AppThemeScheme.ETHEREAL -> ThemePersonality(true, ThemeMotionRecipe.FADE_LONG, OrpheusMotion.DistanceBase)
        AppThemeScheme.ROSE_PINE -> ThemePersonality(true, ThemeMotionRecipe.SLIDE_SHORT, OrpheusMotion.DistanceSmall)
        AppThemeScheme.CATPPUCCIN_MOCHA -> ThemePersonality(true, ThemeMotionRecipe.SCALE_FADE, OrpheusMotion.DistanceBase)
        AppThemeScheme.SAKURA -> ThemePersonality(true, ThemeMotionRecipe.SLIDE_FADE, OrpheusMotion.DistanceBase)
        AppThemeScheme.LIGHT, AppThemeScheme.DARK -> ThemePersonality(false, ThemeMotionRecipe.SLIDE_FADE, OrpheusMotion.DistanceBase)
    }
