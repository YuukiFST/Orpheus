package com.yuukifst.orpheus.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack

@Entity(
    tableName = "youtube_downloads",
    indices = [Index(value = ["video_id"], unique = true)],
)
data class YouTubeDownloadEntity(
    @PrimaryKey
    @ColumnInfo(name = "video_id")
    val videoId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "channel_name")
    val channelName: String,
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "display_title")
    val displayTitle: String? = null,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    @ColumnInfo(name = "downloaded_at")
    val downloadedAt: Long = System.currentTimeMillis(),
)

fun YouTubeDownloadEntity.toYouTubeTrack(): YouTubeTrack = YouTubeTrack(
    videoId = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    displayTitle = displayTitle,
)

fun YouTubeTrack.toDownloadEntity(filePath: String): YouTubeDownloadEntity = YouTubeDownloadEntity(
    videoId = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    displayTitle = displayTitle,
    filePath = filePath,
)
