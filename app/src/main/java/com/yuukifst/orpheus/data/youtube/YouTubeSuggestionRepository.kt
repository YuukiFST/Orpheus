package com.yuukifst.orpheus.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeSuggestionRepository @Inject constructor() {

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return@withContext emptyList()
        YouTubeInitializer.ensureInitialized()
        runCatching {
            ServiceList.YouTube.suggestionExtractor
                .suggestionList(trimmed)
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SUGGESTIONS)
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val MAX_SUGGESTIONS = 8
    }
}
