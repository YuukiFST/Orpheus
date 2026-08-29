package com.yuukifst.orpheus.data.youtube

import android.util.LruCache
import com.yuukifst.orpheus.data.preferences.UserPreferencesRepository
import com.yuukifst.orpheus.data.preferences.YouTubeAudioQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class YouTubeStreamResult(
    val streamUrl: String,
    val mimeType: String?,
)

@Singleton
class YouTubeStreamExtractor @Inject constructor(
    private val youTubeInitializer: YouTubeInitializer,
    private val youTubeDownloader: YouTubeDownloaderImpl,
    private val userPreferencesRepository: UserPreferencesRepository?,
) {

    private val streamCache = LruCache<String, CachedStreamResult>(64)
    private val extractScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightExtracts = YouTubeInFlightShare<String, YouTubeStreamResult>()

    suspend fun extractBestAudio(videoId: String): YouTubeStreamResult = withContext(Dispatchers.IO) {
        val quality = currentQuality()
        val cacheKey = streamCacheKey(videoId, quality)
        val now = System.currentTimeMillis()
        streamCache.get(cacheKey)?.takeIf { it.isValid(now) }?.result?.let { return@withContext it }

        inFlightExtracts.share(cacheKey, extractScope) {
            performExtract(videoId, quality, cacheKey)
        }
    }

    private suspend fun performExtract(
        videoId: String,
        quality: YouTubeAudioQuality,
        cacheKey: String,
    ): YouTubeStreamResult {
        youTubeInitializer.ensureInitialized()
        val info = youTubeDownloader.runAsStream {
            StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
        }
        val best = selectYouTubeAudioStream(info.audioStreams, quality)
            ?: throw IllegalStateException("No audio stream available for $videoId")
        val result = YouTubeStreamResult(
            streamUrl = best.content,
            mimeType = best.format?.mimeType,
        )
        streamCache.put(cacheKey, CachedStreamResult(result, System.currentTimeMillis()))
        return result
    }

    suspend fun extractBestAudioWithRetry(videoId: String): YouTubeStreamResult {
        return try {
            extractBestAudio(videoId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            kotlinx.coroutines.delay(250)
            extractBestAudio(videoId)
        }
    }

    /**
     * Warms [streamCache] for a track the user is likely to tap. Never throws:
     * a prefetch failure must be indistinguishable from not having prefetched.
     * Returns true when the cache now holds a valid entry for [videoId].
     */
    suspend fun prefetchBestAudio(videoId: String): Boolean {
        if (videoId.isBlank()) return false
        if (isCached(videoId)) return true
        return runCatching { extractBestAudio(videoId) }
            .onFailure { error ->
                if (error is CancellationException) throw error
                Timber.tag("YouTubeStreamExtractor").w("Prefetch failed for %s", videoId)
            }
            .isSuccess
    }

    internal fun isCached(videoId: String): Boolean {
        val now = System.currentTimeMillis()
        return YouTubeAudioQuality.entries.any { quality ->
            streamCache.get(streamCacheKey(videoId, quality))?.isValid(now) == true
        }
    }

    internal fun clearStreamCacheForTests() {
        streamCache.evictAll()
    }

    internal fun seedStreamCacheForTests(videoId: String, result: YouTubeStreamResult) {
        streamCache.put(
            streamCacheKey(videoId, YouTubeAudioQuality.HIGH),
            CachedStreamResult(result, System.currentTimeMillis()),
        )
    }

    private suspend fun currentQuality(): YouTubeAudioQuality {
        return userPreferencesRepository?.youtubeAudioQualityFlow?.first()
            ?: YouTubeAudioQuality.HIGH
    }

    private data class CachedStreamResult(
        val result: YouTubeStreamResult,
        val cachedAtMs: Long,
    ) {
        fun isValid(now: Long): Boolean = now - cachedAtMs < STREAM_CACHE_TTL_MS
    }

    internal companion object {
        fun createForTests(): YouTubeStreamExtractor {
            val downloader = YouTubeDownloaderImpl.createStandalone()
            return YouTubeStreamExtractor(
                youTubeInitializer = YouTubeInitializer(downloader),
                youTubeDownloader = downloader,
                userPreferencesRepository = null,
            )
        }

        private const val STREAM_CACHE_TTL_MS = 2 * 60 * 60 * 1000L
    }
}

internal fun streamCacheKey(videoId: String, quality: YouTubeAudioQuality): String =
    "${videoId.trim()}:${quality.name}"

internal fun youtubeBitrateKbps(averageBitrate: Int): Int {
    if (averageBitrate <= 0) return 0
    return if (averageBitrate >= 1000) (averageBitrate + 500) / 1000 else averageBitrate
}

internal fun chooseYouTubeAudioBitrate(
    bitrates: List<Int>,
    quality: YouTubeAudioQuality,
): Int? {
    val usable = bitrates.filter { youtubeBitrateKbps(it) > 0 }
    if (usable.isEmpty()) return bitrates.firstOrNull()
    val capKbps = quality.maxBitrateKbps
    if (capKbps == null) {
        return usable.maxByOrNull { youtubeBitrateKbps(it) }
    }
    return usable
        .filter { youtubeBitrateKbps(it) <= capKbps }
        .maxByOrNull { youtubeBitrateKbps(it) }
        ?: usable.minByOrNull { youtubeBitrateKbps(it) }
}

internal fun selectYouTubeAudioStream(
    streams: List<AudioStream>,
    quality: YouTubeAudioQuality,
): AudioStream? {
    val chosen = chooseYouTubeAudioBitrate(streams.map { it.averageBitrate }, quality) ?: return streams.firstOrNull()
    return streams.firstOrNull { it.averageBitrate == chosen } ?: streams.firstOrNull()
}
