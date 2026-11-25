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
    fun legacyTerminalMapsToDark() {
        val r = resolveAppTheme("terminal", systemDark = false)
        assertTrue(r.darkTheme)
        assertEquals(AppThemeScheme.DARK, r.scheme)
    }

    @Test
    fun followSystemMatchesSystemDark() {
        val light = resolveAppTheme(AppThemeMode.FOLLOW_SYSTEM, systemDark = false)
        val dark = resolveAppTheme(AppThemeMode.FOLLOW_SYSTEM, systemDark = true)
        assertEquals(AppThemeScheme.LIGHT, light.scheme)
        assertEquals(AppThemeScheme.DARK, dark.scheme)
        assertFalse(light.darkTheme)
        assertTrue(dark.darkTheme)
    }

    @Test
    fun sakuraForcesLightAndSakuraScheme() {
        val r = resolveAppTheme(AppThemeMode.SAKURA, systemDark = true)
        assertFalse(r.darkTheme)
        assertEquals(AppThemeScheme.SAKURA, r.scheme)
    }

    @Test
    fun followSystemNeverSelectsColorfulThemes() {
        val light = resolveAppTheme(AppThemeMode.FOLLOW_SYSTEM, systemDark = false)
        val dark = resolveAppTheme(AppThemeMode.FOLLOW_SYSTEM, systemDark = true)
        assertEquals(AppThemeScheme.LIGHT, light.scheme)
        assertEquals(AppThemeScheme.DARK, dark.scheme)
    }

    @Test
    fun unknownModeFallsBackToLight() {
        val r = resolveAppTheme("garbage", systemDark = true)
        assertEquals(AppThemeScheme.LIGHT, r.scheme)
        assertFalse(r.darkTheme)
    }

    @Test
    fun settingsCategoryColors_monoWhenDark() {
        val mono = settingsCategoryColorsOrMono(
            category = SettingsCategory.APPEARANCE,
            appThemeMode = AppThemeMode.DARK,
            monoPair = Color.Black to Color.White
        )
        assertEquals(Color.Black, mono.first)
    }

    @Test
    fun settingsCategoryColors_pastelWhenSakura() {
        val pair = settingsCategoryColorsOrMono(
            category = SettingsCategory.APPEARANCE,
            appThemeMode = AppThemeMode.SAKURA,
            monoPair = Color.Black to Color.White
        )
        assertTrue(pair.first != Color.Black)
    }
}
