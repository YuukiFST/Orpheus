package com.yuukifst.orpheus.data.backup.model

import com.google.gson.annotations.SerializedName
import com.yuukifst.orpheus.data.database.FavoritesEntity

data class YouTubeLikedBackupEntry(
    @SerializedName(value = "videoId", alternate = ["video_id"])
    val videoId: String,
    val title: String,
    @SerializedName(value = "channelName", alternate = ["channel_name"])
    val channelName: String,
    @SerializedName(value = "thumbnailUrl", alternate = ["thumbnail_url"])
    val thumbnailUrl: String = "",
    @SerializedName(value = "durationMs", alternate = ["duration_ms"])
    val durationMs: Long = 0L,
    @SerializedName(value = "displayTitle", alternate = ["display_title"])
    val displayTitle: String? = null,
    @SerializedName(value = "favoritedAt", alternate = ["favorited_at"])
    val favoritedAt: Long? = null,
)

data class LikedBackupPayload(
    val version: Int = 2,
    val local: List<FavoritesEntity> = emptyList(),
    val youtube: List<YouTubeLikedBackupEntry> = emptyList(),
) {
    fun entryCount(): Int = local.size + youtube.size
}
