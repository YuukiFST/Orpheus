package com.yuukifst.orpheus.utils

import com.yuukifst.orpheus.data.model.PlaylistMixedTrack
import com.yuukifst.orpheus.data.model.Song
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack

fun String.isYouTubeMediaId(): Boolean = startsWith("youtube_")

fun Song.isYouTubeSource(): Boolean = id.isYouTubeMediaId()

fun Song.toPlaylistMixedTrack(sortOrder: Int): PlaylistMixedTrack {
    if (id.isYouTubeMediaId()) {
        return PlaylistMixedTrack.YouTube(
            track = YouTubeTrack(
                videoId = id.removePrefix("youtube_"),
                title = title,
                channelName = artist,
                thumbnailUrl = albumArtUriString.orEmpty(),
                durationMs = duration,
            ),
            sortOrder = sortOrder,
        )
    }
    return PlaylistMixedTrack.Local(song = this, sortOrder = sortOrder)
}

fun List<Song>.toPlaylistMixedTracks(): List<PlaylistMixedTrack> =
    mapIndexed { index, song -> song.toPlaylistMixedTrack(index) }
