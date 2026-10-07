package com.yuukifst.orpheus.data.youtube

import android.util.LruCache
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.last
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

    suspend fun search(query: String): List<YouTubeTrack> = searchProgressive(query).last()

    fun searchProgressive(query: String): Flow<List<YouTubeTrack>> = channelFlow {
        withContext(Dispatchers.IO) {
            val key = youtubeQueryCacheKey(query)
            if (key.isBlank()) {
                send(emptyList())
                return@withContext
            }
            searchCache.get(key)?.let { cached ->
                send(cached)
                return@withContext
            }

            val shared = inFlightMutex.withLock {
                inFlightSearches[key]?.takeIf { it.isActive }
            }
            if (shared != null) {
                send(shared.await())
                return@withContext
            }

            coroutineScope {
                val deferred = async {
                    performSearch(query.trim(), key) { firstPage ->
                        trySend(firstPage)
                    }
                }
                inFlightMutex.withLock {
                    inFlightSearches[key] = deferred
                }
                try {
                    send(deferred.await())
                } finally {
                    inFlightMutex.withLock {
                        if (inFlightSearches[key] === deferred) {
                            inFlightSearches.remove(key)
                        }
                    }
                }
            }
        }
    }.buffer(Channel.UNLIMITED).distinctUntilChanged()

    fun cancelActiveRequest() {
        youTubeDownloader.cancelActiveRequest()
    }

    fun warmUpConnection() {
        youTubeDownloader.warmUpConnection()
    }

    @Volatile
    private var contentCountryCode: String? = null

    /** Applies the Settings region hint; cached results ranked for the old region are dropped. */
    fun setContentCountry(code: String) {
        if (contentCountryCode == code) return
        val hadPrevious = contentCountryCode != null
        contentCountryCode = code
        youTubeInitializer.setContentCountry(code)
        if (hadPrevious) searchCache.evictAll()
    }

    private fun performSearch(
        trimmedQuery: String,
        cacheKey: String,
        onFirstPage: ((List<YouTubeTrack>) -> Unit)? = null,
    ): List<YouTubeTrack> {
        youTubeInitializer.ensureInitialized()
        return youTubeDownloader.runAsSearch {
            val handler = YoutubeSearchQueryHandlerFactory.getInstance()
                .fromQuery(trimmedQuery, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
            val searchInfo = SearchInfo.getInfo(ServiceList.YouTube, handler)
            val results = mutableListOf<YouTubeTrack>()
            fun consume(items: List<InfoItem>) {
                val incoming = items.mapNotNull { item -> item.toYouTubeTrack() }
                val merged = mergeYouTubeSearchTracks(results, incoming)
                results.clear()
                results.addAll(merged)
            }
            consume(searchInfo.relatedItems)
            val page1 = results.toList()
            searchCache.put(cacheKey, page1)
            onFirstPage?.invoke(page1)
            var nextPage = searchInfo.nextPage
            var pagesFetched = 1
            while (nextPage != null && pagesFetched < MAX_SEARCH_PAGES) {
                val more = runCatching {
                    SearchInfo.getMoreItems(ServiceList.YouTube, handler, nextPage)
                }.getOrNull() ?: break
                consume(more.items)
                nextPage = more.nextPage
                pagesFetched++
            }
            searchCache.put(cacheKey, results)
            results
        }
    }

    internal fun clearSearchCacheForTests() {
        searchCache.evictAll()
    }

    internal fun seedSearchCacheForTests(query: String, results: List<YouTubeTrack>) {
        searchCache.put(youtubeQueryCacheKey(query), results)
    }

    internal fun searchCachedOnly(query: String): List<YouTubeTrack>? {
        return searchCache.get(youtubeQueryCacheKey(query))
    }

    internal companion object {
        private const val MAX_SEARCH_PAGES = 3

        fun createForTests(): YouTubeSearchRepository {
            val downloader = YouTubeDownloaderImpl.createStandalone()
            return YouTubeSearchRepository(
                youTubeInitializer = YouTubeInitializer(downloader),
                youTubeDownloader = downloader,
            )
        }
    }
}

/** Shared cache key for YouTube search/suggestion memory caches. */
internal fun youtubeQueryCacheKey(query: String): String = query.trim().lowercase()

internal fun mergeYouTubeSearchTracks(
    existing: List<YouTubeTrack>,
    incoming: List<YouTubeTrack>,
): List<YouTubeTrack> {
    val seen = existing.map { it.videoId }.toMutableSet()
    val out = existing.toMutableList()
    for (track in incoming) {
        if (seen.add(track.videoId)) out.add(track)
    }
    return out
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
