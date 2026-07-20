package com.yuukifst.orpheus.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import javax.inject.Inject
import javax.inject.Singleton

data class YouTubeStreamResult(
    val streamUrl: String,
    val mimeType: String?,
)

@Singleton
class YouTubeStreamExtractor @Inject constructor() {

    suspend fun extractBestAudio(videoId: String): YouTubeStreamResult = withContext(Dispatchers.IO) {
        YouTubeInitializer.ensureInitialized()
        val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
        val best = info.audioStreams
            .filter { it.format != MediaFormat.WEBMA_OPUS || true }
            .maxByOrNull { it.averageBitrate }
            ?: info.audioStreams.maxByOrNull { it.averageBitrate }
            ?: throw IllegalStateException("No audio stream available for $videoId")
        YouTubeStreamResult(
            streamUrl = best.content,
            mimeType = best.format?.mimeType,
        )
    }

    suspend fun extractBestAudioWithRetry(videoId: String): YouTubeStreamResult {
        return try {
            extractBestAudio(videoId)
        } catch (_: Exception) {
            extractBestAudio(videoId)
        }
    }
}
