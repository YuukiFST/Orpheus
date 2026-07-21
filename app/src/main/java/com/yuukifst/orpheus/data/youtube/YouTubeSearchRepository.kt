package com.yuukifst.orpheus.data.youtube

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

    suspend fun search(query: String): List<YouTubeTrack> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        YouTubeInitializer.ensureInitialized()
        val handler = YoutubeSearchQueryHandlerFactory.getInstance()
            .fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        // Must use the service overload so NewPipe calls fetchPage() before reading results.
        val searchInfo = SearchInfo.getInfo(ServiceList.YouTube, handler)
        searchInfo.relatedItems
            .mapNotNull { item -> item.toYouTubeTrack() }
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
        thumbnailUrl = thumbnails.firstOrNull()?.url.orEmpty(),
        durationMs = duration * 1000L,
    )
}
