package com.yuukifst.orpheus.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "playlist_youtube_tracks",
    primaryKeys = ["playlist_id", "video_id"],
    indices = [Index(value = ["playlist_id", "sort_order"])],
)
data class PlaylistYouTubeTrackEntity(
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,
    @ColumnInfo(name = "video_id")
    val videoId: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
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
)
