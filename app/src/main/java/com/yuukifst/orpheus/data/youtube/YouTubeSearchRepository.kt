package com.yuukifst.orpheus.data.youtube

import android.util.LruCache
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeSearchRepository @Inject constructor(
    private val youTubeInitializer: YouTubeInitializer,
    private val youTubeDownloader: YouTubeDownloaderImpl,
) {

    private val searchCache = LruCache<String, List<YouTubeTrack>>(32)
    private val inFlightSearches = mutableMapOf<String, Deferred<List<YouTubeTrack>>>()
    private val inFlightMutex = Mutex()

    suspend fun search(query: String): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        if (key.isBlank()) return@withContext emptyList()
        searchCache.get(key)?.let { return@withContext it }

        val shared = inFlightMutex.withLock {
            inFlightSearches[key]?.takeIf { it.isActive }
        }
        if (shared != null) {
            return@withContext shared.await()
        }

        coroutineScope {
            val deferred = async {
                performSearch(query.trim(), key)
            }
            inFlightMutex.withLock {
                inFlightSearches[key] = deferred
            }
            try {
                deferred.await()
            } finally {
                inFlightMutex.withLock {
                    if (inFlightSearches[key] === deferred) {
                        inFlightSearches.remove(key)
                    }
                }
            }
        }
    }

    fun cancelActiveRequest() {
        youTubeDownloader.cancelActiveRequest()
    }

    fun warmUpConnection() {
        youTubeDownloader.warmUpConnection()
    }

    private fun performSearch(trimmedQuery: String, cacheKey: String): List<YouTubeTrack> {
        youTubeInitializer.ensureInitialized()
        val handler = YoutubeSearchQueryHandlerFactory.getInstance()
            .fromQuery(trimmedQuery, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        val searchInfo = SearchInfo.getInfo(ServiceList.YouTube, handler)
        val results = searchInfo.relatedItems
            .mapNotNull { item -> item.toYouTubeTrack() }
        searchCache.put(cacheKey, results)
        return results
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

    internal companion object {
        fun createForTests(): YouTubeSearchRepository {
            val downloader = YouTubeDownloaderImpl.createStandalone()
            return YouTubeSearchRepository(
                youTubeInitializer = YouTubeInitializer(downloader),
                youTubeDownloader = downloader,
            )
        }
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
        channelName = preferPrimaryYouTubeUploader(uploaderName.orEmpty()),
        thumbnailUrl = selectBestThumbnailUrl(thumbnails, id),
        durationMs = duration * 1000L,
    )
}

/**
 * YouTube search bylines for collabs concatenate artists ("A and B") while the
 * publishing channel is the first name (matches StreamInfo.uploaderName / uploaderUrl).
 */
internal fun preferPrimaryYouTubeUploader(uploaderName: String): String {
    if (uploaderName.isBlank()) return uploaderName
    val delimiters = listOf(" and ", " & ", " e ")
    for (delimiter in delimiters) {
        val index = uploaderName.indexOf(delimiter, ignoreCase = true)
        if (index > 0) {
            return uploaderName.substring(0, index).trim().ifBlank { uploaderName }
        }
    }
    return uploaderName
}
