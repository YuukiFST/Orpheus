package com.yuukifst.orpheus.data.youtube

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeSuggestionRepository @Inject constructor(
    private val youTubeInitializer: YouTubeInitializer,
) {

    private val suggestionCache = LruCache<String, List<String>>(64)

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return@withContext emptyList()
        val key = youtubeQueryCacheKey(trimmed)
        suggestionCache.get(key)?.let { return@withContext it }
        youTubeInitializer.ensureInitialized()
        val fetched = runCatching {
            ServiceList.YouTube.suggestionExtractor
                .suggestionList(trimmed)
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SUGGESTIONS)
        }.getOrDefault(emptyList())
        if (fetched.isNotEmpty()) {
            suggestionCache.put(key, fetched)
        }
        fetched
    }

    internal fun suggestionsCachedOnly(query: String): List<String>? =
        suggestionCache.get(youtubeQueryCacheKey(query))

    internal fun clearSuggestionCacheForTests() {
        suggestionCache.evictAll()
    }

    internal fun seedSuggestionCacheForTests(query: String, suggestions: List<String>) {
        suggestionCache.put(youtubeQueryCacheKey(query), suggestions)
    }

    internal companion object {
        fun createForTests(): YouTubeSuggestionRepository {
            return YouTubeSuggestionRepository(
                youTubeInitializer = YouTubeInitializer(YouTubeDownloaderImpl.createStandalone()),
            )
        }

        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_SUGGESTIONS = 8
    }
}
