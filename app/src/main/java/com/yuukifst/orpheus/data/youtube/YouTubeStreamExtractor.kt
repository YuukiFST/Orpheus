package com.yuukifst.orpheus.data.youtube

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.StreamInfo
import javax.inject.Inject
import javax.inject.Singleton

data class YouTubeStreamResult(
    val streamUrl: String,
    val mimeType: String?,
)

@Singleton
class YouTubeStreamExtractor @Inject constructor(
    private val youTubeInitializer: YouTubeInitializer,
) {

    private val streamCache = LruCache<String, CachedStreamResult>(64)

    suspend fun extractBestAudio(videoId: String): YouTubeStreamResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        streamCache.get(videoId)?.takeIf { it.isValid(now) }?.result?.let { return@withContext it }

        youTubeInitializer.ensureInitialized()
        val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
        val best = info.audioStreams
            .maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("No audio stream available for $videoId")
        val result = YouTubeStreamResult(
            streamUrl = best.content,
            mimeType = best.format?.mimeType,
        )
        streamCache.put(videoId, CachedStreamResult(result, now))
        result
    }

    suspend fun extractBestAudioWithRetry(videoId: String): YouTubeStreamResult {
        return try {
            extractBestAudio(videoId)
        } catch (_: Exception) {
            kotlinx.coroutines.delay(250)
            extractBestAudio(videoId)
        }
    }

    internal fun clearStreamCacheForTests() {
        streamCache.evictAll()
    }

    private data class CachedStreamResult(
        val result: YouTubeStreamResult,
        val cachedAtMs: Long,
    ) {
        fun isValid(now: Long): Boolean = now - cachedAtMs < STREAM_CACHE_TTL_MS
    }

    private companion object {
        const val STREAM_CACHE_TTL_MS = 2 * 60 * 60 * 1000L
    }
}
