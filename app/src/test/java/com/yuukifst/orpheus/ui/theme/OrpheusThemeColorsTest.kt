package com.yuukifst.orpheus.ui.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrpheusThemeColorsTest {

    @Test
    fun darkScheme_usesBlackBackgroundAndWhiteForeground() {
        assertEquals(MonoBlack, DarkColorScheme.background)
        assertEquals(MonoWhite, DarkColorScheme.onBackground)
        assertEquals(MonoWhite, DarkColorScheme.onSurface)
        assertEquals(MonoWhite, DarkColorScheme.outline)
    }

    @Test
    fun lightScheme_usesWhiteBackgroundAndBlackForeground() {
        assertEquals(MonoWhite, LightColorScheme.background)
        assertEquals(MonoBlack, LightColorScheme.onBackground)
        assertEquals(MonoBlack, LightColorScheme.onSurface)
        assertEquals(MonoBlack, LightColorScheme.outline)
    }

    @Test
    fun darkScheme_primaryPairUsesWhiteOnBlack() {
        assertNotEquals(DarkColorScheme.primary, DarkColorScheme.onPrimary)
        assertTrue(DarkColorScheme.primary.luminance() > 0.9f)
        assertTrue(DarkColorScheme.onPrimary.luminance() < 0.1f)
    }

    @Test
    fun lightScheme_outlineIsBlackNotAccent() {
        assertEquals(MonoBlack, LightColorScheme.outline)
        assertNotEquals(LightColorScheme.outline, VantaAccent)
    }
}
