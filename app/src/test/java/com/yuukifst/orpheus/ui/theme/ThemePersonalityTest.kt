package com.yuukifst.orpheus.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePersonalityTest {
    @Test
    fun softChromeForColorfulSchemes() {
        assertTrue(themePersonalityFor(AppThemeScheme.PIXEL, useSmoothCorners = true).softChrome)
        assertTrue(themePersonalityFor(AppThemeScheme.ETHEREAL, useSmoothCorners = true).softChrome)
        assertTrue(themePersonalityFor(AppThemeScheme.SAKURA, useSmoothCorners = true).softChrome)
        assertFalse(themePersonalityFor(AppThemeScheme.DARK, useSmoothCorners = true).softChrome)
    }

    @Test
    fun motionRecipesMatchSpec() {
        assertEquals(ThemeMotionRecipe.FADE_LONG, themePersonalityFor(AppThemeScheme.ETHEREAL, true).motionRecipe)
        assertEquals(ThemeMotionRecipe.SLIDE_SHORT, themePersonalityFor(AppThemeScheme.ROSE_PINE, true).motionRecipe)
        assertEquals(ThemeMotionRecipe.SCALE_FADE, themePersonalityFor(AppThemeScheme.CATPPUCCIN_MOCHA, true).motionRecipe)
        assertEquals(ThemeMotionRecipe.SLIDE_FADE, themePersonalityFor(AppThemeScheme.SAKURA, true).motionRecipe)
    }
}
