package com.yuukifst.orpheus.data.youtube

import android.content.Context
import com.yuukifst.orpheus.data.database.YouTubeDownloadDao
import com.yuukifst.orpheus.data.database.toDownloadEntity
import com.yuukifst.orpheus.data.media.SongMetadataEditor
import com.yuukifst.orpheus.data.database.toYouTubeTrack
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: YouTubeDownloadDao,
    private val streamExtractor: YouTubeStreamExtractor,
    private val playbackResolver: YouTubePlaybackResolver,
    private val metadataEditor: SongMetadataEditor,
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun download(track: YouTubeTrack): Result<YouTubeTrack> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = downloadDao.getByVideoId(track.videoId)
            if (existing != null && File(existing.filePath).exists()) {
                return@runCatching existing.toYouTubeTrack()
            }

            val stream = streamExtractor.extractBestAudioWithRetry(track.videoId)
            val ext = when {
                stream.mimeType?.contains("mp4") == true -> "m4a"
                stream.mimeType?.contains("webm") == true -> "webm"
                else -> "m4a"
            }
            val safeName = track.effectiveTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
            val outFile = File(playbackResolver.getOrpheusMusicDir(), "${safeName}_${track.videoId}.$ext")

            val request = Request.Builder().url(stream.streamUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
                response.body?.byteStream()?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Empty response body")
            }

            metadataEditor.writeYouTubeTags(
                filePath = outFile.absolutePath,
                title = track.effectiveTitle,
                thumbnailUrl = track.thumbnailUrl,
            )

            val entity = track.toDownloadEntity(outFile.absolutePath)
            downloadDao.upsert(entity)
            entity.toYouTubeTrack()
        }
    }

    suspend fun delete(videoId: String) = withContext(Dispatchers.IO) {
        val entity = downloadDao.getByVideoId(videoId) ?: return@withContext
        File(entity.filePath).delete()
        downloadDao.delete(videoId)
    }

    suspend fun rename(videoId: String, displayTitle: String?) {
        downloadDao.updateDisplayTitle(videoId, displayTitle)
        val entity = downloadDao.getByVideoId(videoId) ?: return
        metadataEditor.writeYouTubeTags(
            filePath = entity.filePath,
            title = displayTitle?.takeIf { it.isNotBlank() } ?: entity.title,
            thumbnailUrl = entity.thumbnailUrl,
        )
    }
}
