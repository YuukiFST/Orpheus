package com.yuukifst.orpheus.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Empty code = NewPipe's default content country. */
const val YOUTUBE_SEARCH_REGION_DEFAULT = ""

/** ISO 3166 codes offered in Settings; YouTube uses this as the `gl` ranking hint. */
val YOUTUBE_SEARCH_REGION_CODES = listOf(
    YOUTUBE_SEARCH_REGION_DEFAULT,
    "US", "GB", "CA", "AU", "IE", "DE", "FR", "ES", "IT", "NL", "SE", "JP", "KR", "MX", "AR",
)

internal fun sanitizeYouTubeSearchRegion(raw: String?): String {
    val code = raw?.trim()?.uppercase().orEmpty()
    return if (code in YOUTUBE_SEARCH_REGION_CODES) code else YOUTUBE_SEARCH_REGION_DEFAULT
}

/**
 * YouTube Search result filters. Kept out of [UserPreferencesRepository] (already ~2k lines);
 * shares the same "settings" DataStore.
 */
@Singleton
class YouTubeSearchFilterPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val SEARCH_REGION = stringPreferencesKey("youtube_search_region")
        val HIDE_PORTUGUESE = booleanPreferencesKey("youtube_search_hide_portuguese")
    }

    val searchRegionFlow: Flow<String> =
        dataStore.data.map { preferences ->
            sanitizeYouTubeSearchRegion(preferences[Keys.SEARCH_REGION])
        }.distinctUntilChanged()

    val hidePortugueseFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[Keys.HIDE_PORTUGUESE] ?: false
        }.distinctUntilChanged()

    suspend fun setSearchRegion(code: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SEARCH_REGION] = sanitizeYouTubeSearchRegion(code)
        }
    }

    suspend fun setHidePortuguese(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.HIDE_PORTUGUESE] = enabled
        }
    }
}
