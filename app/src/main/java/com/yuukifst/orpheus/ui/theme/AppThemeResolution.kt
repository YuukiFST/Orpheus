package com.yuukifst.orpheus.ui.theme

import com.yuukifst.orpheus.data.preferences.AppThemeMode

enum class AppThemeScheme { LIGHT, DARK, PIXEL }

data class ResolvedAppTheme(val darkTheme: Boolean, val scheme: AppThemeScheme)

fun resolveAppTheme(appThemeMode: String, systemDark: Boolean): ResolvedAppTheme =
    when (appThemeMode) {
        AppThemeMode.DARK -> ResolvedAppTheme(darkTheme = true, scheme = AppThemeScheme.DARK)
        AppThemeMode.PIXEL -> ResolvedAppTheme(darkTheme = true, scheme = AppThemeScheme.PIXEL)
        AppThemeMode.FOLLOW_SYSTEM -> ResolvedAppTheme(
            darkTheme = systemDark,
            scheme = if (systemDark) AppThemeScheme.DARK else AppThemeScheme.LIGHT
        )
        else -> ResolvedAppTheme(darkTheme = false, scheme = AppThemeScheme.LIGHT) // LIGHT + unknown
    }
