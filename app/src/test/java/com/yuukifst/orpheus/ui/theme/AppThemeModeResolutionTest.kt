package com.yuukifst.orpheus.ui.theme

import androidx.compose.ui.graphics.Color
import com.yuukifst.orpheus.data.preferences.AppThemeMode
import com.yuukifst.orpheus.presentation.model.SettingsCategory
import com.yuukifst.orpheus.presentation.screens.settingsCategoryColorsOrMono
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeModeResolutionTest {
    @Test
    fun pixelForcesDarkFlagAndPixelSchemeKey() {
        val r = resolveAppTheme(AppThemeMode.PIXEL, systemDark = false)
        assertTrue(r.darkTheme)
        assertEquals(AppThemeScheme.PIXEL, r.scheme)
    }

    @Test
    fun followSystemNeverSelectsPixel() {
        val light = resolveAppTheme(AppThemeMode.FOLLOW_SYSTEM, systemDark = false)
        val dark = resolveAppTheme(AppThemeMode.FOLLOW_SYSTEM, systemDark = true)
        assertEquals(AppThemeScheme.LIGHT, light.scheme)
        assertEquals(AppThemeScheme.DARK, dark.scheme)
        assertFalse(light.darkTheme)
        assertTrue(dark.darkTheme)
    }

    @Test
    fun unknownModeFallsBackToLight() {
        val r = resolveAppTheme("garbage", systemDark = true)
        assertEquals(AppThemeScheme.LIGHT, r.scheme)
        assertFalse(r.darkTheme)
    }

    @Test
    fun settingsCategoryColors_monoWhenNotPixel() {
        val mono = settingsCategoryColorsOrMono(
            category = SettingsCategory.APPEARANCE,
            isDark = true,
            appThemeMode = AppThemeMode.DARK,
            monoPair = Color.Black to Color.White
        )
        assertEquals(Color.Black, mono.first)
    }

    @Test
    fun settingsCategoryColors_pastelWhenPixel() {
        val pair = settingsCategoryColorsOrMono(
            category = SettingsCategory.APPEARANCE,
            isDark = true,
            appThemeMode = AppThemeMode.PIXEL,
            monoPair = Color.Black to Color.White
        )
        assertTrue(pair.first != Color.Black)
    }
}
