package com.yuukifst.orpheus.data.youtube.model

import androidx.compose.runtime.Immutable

@Immutable
data class YouTubeTrack(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val displayTitle: String? = null,
) {
    val effectiveTitle: String get() = displayTitle?.takeIf { it.isNotBlank() } ?: title

    val mediaId: String get() = "youtube_$videoId"
}
