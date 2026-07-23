package com.yuukifst.orpheus.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrpheusThemeColorsTest {

    @Test
    fun darkScheme_hasSurfaceDepthBeyondBackground() {
        assertNotEquals(DarkColorScheme.background, DarkColorScheme.surface)
        assertNotEquals(DarkColorScheme.surfaceContainerLow, DarkColorScheme.surfaceContainerHighest)
    }

    @Test
    fun darkScheme_primaryPairUsesPhosphorAccentOnDarkInk() {
        assertNotEquals(DarkColorScheme.primary, DarkColorScheme.onPrimary)
        assertTrue(DarkColorScheme.onPrimary.luminance() < 0.15f)
        assertTrue(VantaAccent.luminance() > 0.4f)
    }

    @Test
    fun lightScheme_outlineIsNotNeonAccent() {
        assertNotEquals(LightColorScheme.outline, VantaAccent)
    }
}
