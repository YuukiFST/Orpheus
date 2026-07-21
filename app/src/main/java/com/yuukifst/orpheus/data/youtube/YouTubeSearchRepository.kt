package com.yuukifst.orpheus.data.youtube

import android.util.LruCache
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeSearchRepository @Inject constructor() {

    private val searchCache = LruCache<String, List<YouTubeTrack>>(32)

    suspend fun search(query: String): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        if (key.isBlank()) return@withContext emptyList()
        searchCache.get(key)?.let { return@withContext it }

        YouTubeInitializer.ensureInitialized()
        val handler = YoutubeSearchQueryHandlerFactory.getInstance()
            .fromQuery(query.trim(), listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        // Must use the service overload so NewPipe calls fetchPage() before reading results.
        val searchInfo = SearchInfo.getInfo(ServiceList.YouTube, handler)
        val results = searchInfo.relatedItems
            .mapNotNull { item -> item.toYouTubeTrack() }
        searchCache.put(key, results)
        results
    }

    internal fun clearSearchCacheForTests() {
        searchCache.evictAll()
    }

    internal fun seedSearchCacheForTests(query: String, results: List<YouTubeTrack>) {
        searchCache.put(query.trim().lowercase(), results)
    }

    internal fun searchCachedOnly(query: String): List<YouTubeTrack>? {
        return searchCache.get(query.trim().lowercase())
    }
}

internal fun extractYouTubeVideoId(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return runCatching {
        ServiceList.YouTube.streamLHFactory.fromUrl(url).id
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun InfoItem.toYouTubeTrack(): YouTubeTrack? {
    if (this !is StreamInfoItem) return null
    val id = extractYouTubeVideoId(url) ?: return null
    return YouTubeTrack(
        videoId = id,
        title = name.orEmpty(),
        channelName = uploaderName.orEmpty(),
        thumbnailUrl = selectBestThumbnailUrl(thumbnails, id),
        durationMs = duration * 1000L,
    )
}
