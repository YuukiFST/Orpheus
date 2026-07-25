package com.yuukifst.orpheus.ui.theme

import com.yuukifst.orpheus.data.preferences.AppThemeMode

enum class AppThemeScheme {
    LIGHT, DARK, PIXEL, ETHEREAL, ROSE_PINE, CATPPUCCIN_MOCHA, SAKURA
}

data class ResolvedAppTheme(val darkTheme: Boolean, val scheme: AppThemeScheme)

fun resolveAppTheme(appThemeMode: String, systemDark: Boolean): ResolvedAppTheme =
    when (appThemeMode) {
        AppThemeMode.DARK -> ResolvedAppTheme(darkTheme = true, scheme = AppThemeScheme.DARK)
        AppThemeMode.PIXEL -> ResolvedAppTheme(darkTheme = true, scheme = AppThemeScheme.PIXEL)
        AppThemeMode.ETHEREAL -> ResolvedAppTheme(darkTheme = true, scheme = AppThemeScheme.ETHEREAL)
        AppThemeMode.ROSE_PINE -> ResolvedAppTheme(darkTheme = false, scheme = AppThemeScheme.ROSE_PINE)
        AppThemeMode.CATPPUCCIN_MOCHA -> ResolvedAppTheme(darkTheme = true, scheme = AppThemeScheme.CATPPUCCIN_MOCHA)
        AppThemeMode.SAKURA -> ResolvedAppTheme(darkTheme = false, scheme = AppThemeScheme.SAKURA)
        // Legacy "terminal" theme → dark (Terminal mode removed)
        "terminal" -> ResolvedAppTheme(darkTheme = true, scheme = AppThemeScheme.DARK)
        AppThemeMode.FOLLOW_SYSTEM -> ResolvedAppTheme(
            darkTheme = systemDark,
            scheme = if (systemDark) AppThemeScheme.DARK else AppThemeScheme.LIGHT
        )
        else -> ResolvedAppTheme(darkTheme = false, scheme = AppThemeScheme.LIGHT) // LIGHT + unknown
    }
