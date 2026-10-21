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

/** Language YouTube Search results are kept in; [languageTag] is the BCP 47 tag ("" for any). */
enum class YouTubeResultLanguage(val languageTag: String) {
    ANY(""),
    ENGLISH("en"),
    PORTUGUESE("pt"),
    SPANISH("es"),
    FRENCH("fr"),
    GERMAN("de"),
    ITALIAN("it"),
    ;

    companion object {
        fun fromLanguageTag(tag: String?): YouTubeResultLanguage? = entries.find { it.languageTag == tag }
    }
}

/**
 * Stored result language; before it existed, "hide Portuguese" was the only filter, and its users
 * asked for English-only results, so it maps to [YouTubeResultLanguage.ENGLISH].
 */
internal fun resolveYouTubeResultLanguage(storedTag: String?, legacyHidePortuguese: Boolean?): YouTubeResultLanguage =
    YouTubeResultLanguage.fromLanguageTag(storedTag)
        ?: if (legacyHidePortuguese == true) YouTubeResultLanguage.ENGLISH else YouTubeResultLanguage.ANY

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
        val LEGACY_HIDE_PORTUGUESE = booleanPreferencesKey("youtube_search_hide_portuguese")
        val RESULT_LANGUAGE = stringPreferencesKey("youtube_search_result_language")
    }

    val searchRegionFlow: Flow<String> =
        dataStore.data.map { preferences ->
            sanitizeYouTubeSearchRegion(preferences[Keys.SEARCH_REGION])
        }.distinctUntilChanged()

    val resultLanguageFlow: Flow<YouTubeResultLanguage> =
        dataStore.data.map { preferences ->
            resolveYouTubeResultLanguage(
                storedTag = preferences[Keys.RESULT_LANGUAGE],
                legacyHidePortuguese = preferences[Keys.LEGACY_HIDE_PORTUGUESE],
            )
        }.distinctUntilChanged()

    suspend fun setSearchRegion(code: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SEARCH_REGION] = sanitizeYouTubeSearchRegion(code)
        }
    }

    suspend fun setResultLanguage(language: YouTubeResultLanguage) {
        dataStore.edit { preferences ->
            preferences[Keys.RESULT_LANGUAGE] = language.languageTag
            preferences.remove(Keys.LEGACY_HIDE_PORTUGUESE)
        }
    }
}
