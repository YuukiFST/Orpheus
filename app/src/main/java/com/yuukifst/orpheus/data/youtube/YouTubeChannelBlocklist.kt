package com.yuukifst.orpheus.data.youtube

import androidx.compose.runtime.Immutable
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack

/** YouTube channel whose videos stay out of search results. */
@Immutable
data class BlockedYouTubeChannel(
    val name: String,
    val url: String? = null,
) {
    /** The URL identifies the channel; the name is the fallback when either side lacks one. */
    fun matches(track: YouTubeTrack): Boolean =
        if (url != null && track.channelUrl != null) {
            url == track.channelUrl
        } else {
            name.trim().equals(track.channelName.trim(), ignoreCase = true)
        }

    internal fun toStorage(): String = url.orEmpty() + SEPARATOR + name

    internal companion object {
        private const val SEPARATOR = '\n'

        fun fromStorage(raw: String): BlockedYouTubeChannel? {
            val name = raw.substringAfter(SEPARATOR, missingDelimiterValue = "")
            if (name.isBlank()) return null
            return BlockedYouTubeChannel(
                name = name,
                url = raw.substringBefore(SEPARATOR).takeIf { it.isNotBlank() },
            )
        }
    }
}

internal fun YouTubeTrack.toBlockedChannelOrNull(): BlockedYouTubeChannel? =
    channelName.trim().takeIf { it.isNotEmpty() }?.let { BlockedYouTubeChannel(name = it, url = channelUrl) }

internal fun filterBlockedChannels(
    results: List<YouTubeTrack>,
    blocked: List<BlockedYouTubeChannel>,
): List<YouTubeTrack> =
    if (blocked.isEmpty()) results else results.filterNot { track -> blocked.any { it.matches(track) } }
