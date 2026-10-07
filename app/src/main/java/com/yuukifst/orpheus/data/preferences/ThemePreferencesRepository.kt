package com.yuukifst.orpheus.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    // Same file as UserPreferencesRepository's startup mirror: already loaded on the startup path.
    private val startupMirror by lazy {
        context.getSharedPreferences(STARTUP_MIRROR_PREFS, Context.MODE_PRIVATE)
    }
    private object Keys {
        val PLAYER_THEME_PREFERENCE = stringPreferencesKey("player_theme_preference_v2")
        val ALBUM_ART_PALETTE_STYLE = stringPreferencesKey("album_art_palette_style_v1")
        val ALBUM_ART_COLOR_ACCURACY = intPreferencesKey("album_art_color_accuracy_v1")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
    }

    val appThemeModeFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.APP_THEME_MODE] ?: AppThemeMode.LIGHT
    }.onEach { mode ->
        if (startupMirror.getString(KEY_APP_THEME_MODE, null) != mode) {
            startupMirror.edit().putString(KEY_APP_THEME_MODE, mode).apply()
        }
    }

    /**
     * Last theme mode seen, read synchronously so the first frame uses the right theme instead
     * of flashing light and recomposing once DataStore emits.
     *
     * Example: `collectAsStateWithLifecycle(initialValue = themePreferencesRepository.readAppThemeModeSync())`.
     */
    fun readAppThemeModeSync(): String =
        startupMirror.getString(KEY_APP_THEME_MODE, null) ?: AppThemeMode.LIGHT

    val playerThemePreferenceFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.PLAYER_THEME_PREFERENCE] ?: ThemePreference.DEFAULT
    }

    val albumArtPaletteStyleFlow: Flow<AlbumArtPaletteStyle> = dataStore.data.map { preferences ->
        AlbumArtPaletteStyle.fromStorageKey(preferences[Keys.ALBUM_ART_PALETTE_STYLE])
    }

    val albumArtColorAccuracyFlow: Flow<Int> = dataStore.data.map { preferences ->
        AlbumArtColorAccuracy.clamp(preferences[Keys.ALBUM_ART_COLOR_ACCURACY] ?: AlbumArtColorAccuracy.DEFAULT)
    }

    suspend fun setPlayerThemePreference(themeMode: String) =
        dataStore.edit { preferences ->
            preferences[Keys.PLAYER_THEME_PREFERENCE] = themeMode
        }

    suspend fun setAppThemeMode(themeMode: String) =
        dataStore.edit { preferences ->
            preferences[Keys.APP_THEME_MODE] = themeMode
        }

    suspend fun initializeAppThemeMode(themeMode: String) =
        dataStore.edit { preferences ->
            if (preferences[Keys.APP_THEME_MODE] == null) {
                preferences[Keys.APP_THEME_MODE] = themeMode
            }
        }

    suspend fun initializePlayerThemePreference(themeMode: String) =
        dataStore.edit { preferences ->
            if (preferences[Keys.PLAYER_THEME_PREFERENCE] == null) {
                preferences[Keys.PLAYER_THEME_PREFERENCE] = themeMode
            }
        }

    suspend fun setAlbumArtPaletteStyle(style: AlbumArtPaletteStyle) =
        dataStore.edit { preferences ->
            preferences[Keys.ALBUM_ART_PALETTE_STYLE] = style.storageKey
        }

    suspend fun setAlbumArtColorAccuracy(level: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.ALBUM_ART_COLOR_ACCURACY] = AlbumArtColorAccuracy.clamp(level)
        }

    private companion object {
        const val STARTUP_MIRROR_PREFS = "orpheus_startup_mirror"
        const val KEY_APP_THEME_MODE = "app_theme_mode"
    }

    suspend fun setAlbumArtPaletteSettings(
        style: AlbumArtPaletteStyle,
        accuracyLevel: Int
    ) = dataStore.edit { preferences ->
        preferences[Keys.ALBUM_ART_PALETTE_STYLE] = style.storageKey
        preferences[Keys.ALBUM_ART_COLOR_ACCURACY] = AlbumArtColorAccuracy.clamp(accuracyLevel)
    }
}
