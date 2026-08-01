package com.yuukifst.orpheus.data.stats

import com.yuukifst.orpheus.data.database.SongEngagementEntity
import com.yuukifst.orpheus.data.model.Song
import com.yuukifst.orpheus.utils.isYouTubeMediaId

enum class TopPlayedFilter {
    ALL,
    LOCAL,
    YOUTUBE,
}

data class TopPlayedEntry(
    val songId: String,
    val title: String,
    val artist: String,
    val albumArtUri: String?,
    val playCount: Int,
)

fun rankTopPlayed(
    engagements: List<SongEngagementEntity>,
    songsById: Map<String, Song>,
    filter: TopPlayedFilter,
    limit: Int = 20,
): List<TopPlayedEntry> {
    if (engagements.isEmpty() || limit <= 0) return emptyList()

    return engagements
        .asSequence()
        .filter { engagement -> engagement.matchesFilter(filter) }
        .filter { engagement -> songsById.containsKey(engagement.songId) }
        .sortedWith(
            compareByDescending<SongEngagementEntity> { it.playCount }
                .thenByDescending { it.lastPlayedTimestamp }
        )
        .take(limit)
        .map { engagement ->
            val song = songsById.getValue(engagement.songId)
            TopPlayedEntry(
                songId = engagement.songId,
                title = song.title,
                artist = song.displayArtist,
                albumArtUri = song.albumArtUriString,
                playCount = engagement.playCount,
            )
        }
        .toList()
}

private fun SongEngagementEntity.matchesFilter(filter: TopPlayedFilter): Boolean {
    return when (filter) {
        TopPlayedFilter.ALL -> true
        TopPlayedFilter.LOCAL -> !songId.isYouTubeMediaId()
        TopPlayedFilter.YOUTUBE -> songId.isYouTubeMediaId()
    }
}
