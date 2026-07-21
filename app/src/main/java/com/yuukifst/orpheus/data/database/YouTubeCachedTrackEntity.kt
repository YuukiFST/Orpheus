package com.yuukifst.orpheus.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yuukifst.orpheus.data.model.Song
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack

@Entity(
    tableName = "youtube_cached_tracks",
    indices = [Index(value = ["video_id"], unique = true)],
)
data class YouTubeCachedTrackEntity(
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
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: Long = 0L,
    @ColumnInfo(name = "favorited_at")
    val favoritedAt: Long? = null,
)

fun YouTubeCachedTrackEntity.mediaId(): String = "youtube_$videoId"

fun YouTubeCachedTrackEntity.toSong(): Song = Song(
    id = mediaId(),
    title = displayTitle?.takeIf { it.isNotBlank() } ?: title,
    artist = channelName,
    artistId = -1L,
    album = "YouTube",
    albumId = -1L,
    path = "",
    contentUriString = "",
    albumArtUriString = thumbnailUrl.takeIf { it.isNotBlank() },
    duration = durationMs,
    mimeType = null,
    bitrate = null,
    sampleRate = null,
)

fun YouTubeTrack.toCachedTrackEntity(
    isFavorite: Boolean = false,
    lastPlayedAt: Long = System.currentTimeMillis(),
    favoritedAt: Long? = if (isFavorite) System.currentTimeMillis() else null,
): YouTubeCachedTrackEntity = YouTubeCachedTrackEntity(
    videoId = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    displayTitle = displayTitle,
    isFavorite = isFavorite,
    lastPlayedAt = lastPlayedAt,
    favoritedAt = favoritedAt,
)
