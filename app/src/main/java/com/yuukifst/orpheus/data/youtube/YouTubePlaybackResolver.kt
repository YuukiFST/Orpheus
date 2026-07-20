package com.yuukifst.orpheus.data.youtube

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.yuukifst.orpheus.data.database.YouTubeDownloadDao
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import com.yuukifst.orpheus.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubePlaybackResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: YouTubeDownloadDao,
    private val streamExtractor: YouTubeStreamExtractor,
) {
    suspend fun resolveMediaItem(track: YouTubeTrack): MediaItem {
        val download = downloadDao.getByVideoId(track.videoId)
        if (download != null) {
            val file = File(download.filePath)
            if (file.exists()) {
                return buildLocalMediaItem(track, file.toUri())
            }
        }
        val stream = streamExtractor.extractBestAudioWithRetry(track.videoId)
        return buildStreamMediaItem(track, stream.streamUrl, stream.mimeType)
    }

    private fun buildLocalMediaItem(track: YouTubeTrack, uri: Uri): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.mediaId)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.effectiveTitle)
                    .setArtist(track.channelName)
                    .setArtworkUri(track.thumbnailUrl.takeIf { it.isNotBlank() }?.toUri())
                    .setExtras(android.os.Bundle().apply {
                        putString(MediaItemBuilder.EXTERNAL_EXTRA_YOUTUBE_VIDEO_ID, track.videoId)
                        putString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH, uri.path)
                    })
                    .build(),
            )
            .build()
    }

    private fun buildStreamMediaItem(track: YouTubeTrack, url: String, mimeType: String?): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.mediaId)
            .setUri(url)
            .setMimeType(mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.effectiveTitle)
                    .setArtist(track.channelName)
                    .setArtworkUri(track.thumbnailUrl.takeIf { it.isNotBlank() }?.toUri())
                    .setExtras(android.os.Bundle().apply {
                        putString(MediaItemBuilder.EXTERNAL_EXTRA_YOUTUBE_VIDEO_ID, track.videoId)
                    })
                    .build(),
            )
            .build()
    }

    fun getOrpheusMusicDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Orpheus",
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
