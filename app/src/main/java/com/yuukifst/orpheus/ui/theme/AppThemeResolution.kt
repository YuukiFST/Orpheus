package com.yuukifst.orpheus.ui.theme

import com.yuukifst.orpheus.data.preferences.AppThemeMode

enum class AppThemeScheme {
    LIGHT, DARK, SAKURA
}

data class ResolvedAppTheme(val darkTheme: Boolean, val scheme: AppThemeScheme)

fun resolveAppTheme(appThemeMode: String, systemDark: Boolean): ResolvedAppTheme =
    when (AppThemeMode.fromStored(appThemeMode)) {
        AppThemeMode.DARK -> ResolvedAppTheme(darkTheme = true, scheme = AppThemeScheme.DARK)
        AppThemeMode.SAKURA -> ResolvedAppTheme(darkTheme = false, scheme = AppThemeScheme.SAKURA)
        AppThemeMode.FOLLOW_SYSTEM -> ResolvedAppTheme(
            darkTheme = systemDark,
            scheme = if (systemDark) AppThemeScheme.DARK else AppThemeScheme.LIGHT
        )
        else -> ResolvedAppTheme(darkTheme = false, scheme = AppThemeScheme.LIGHT)
    }
