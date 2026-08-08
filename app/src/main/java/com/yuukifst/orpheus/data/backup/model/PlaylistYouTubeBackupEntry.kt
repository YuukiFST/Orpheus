package com.yuukifst.orpheus.data.backup.model

import com.google.gson.annotations.SerializedName

/** YouTube Search track membership for a playlist in backup payloads. */
data class PlaylistYouTubeBackupEntry(
    @SerializedName(value = "playlistId", alternate = ["playlist_id"])
    val playlistId: String,
    @SerializedName(value = "videoId", alternate = ["video_id"])
    val videoId: String,
    @SerializedName(value = "sortOrder", alternate = ["sort_order"])
    val sortOrder: Int = 0,
    val title: String = "",
    @SerializedName(value = "channelName", alternate = ["channel_name"])
    val channelName: String = "",
    @SerializedName(value = "thumbnailUrl", alternate = ["thumbnail_url"])
    val thumbnailUrl: String = "",
    @SerializedName(value = "durationMs", alternate = ["duration_ms"])
    val durationMs: Long = 0L,
    @SerializedName(value = "displayTitle", alternate = ["display_title"])
    val displayTitle: String? = null,
)
