package com.yuukifst.orpheus.data.playlist

import com.yuukifst.orpheus.data.database.LocalPlaylistDao
import com.yuukifst.orpheus.data.database.PlaylistYouTubeTrackEntity
import com.yuukifst.orpheus.data.database.YouTubePlaylistDao
import com.yuukifst.orpheus.data.model.PlaylistMixedTrack
import com.yuukifst.orpheus.data.repository.MusicRepository
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistMixedTrackResolver @Inject constructor(
    private val localPlaylistDao: LocalPlaylistDao,
    private val youTubePlaylistDao: YouTubePlaylistDao,
    private val musicRepository: MusicRepository,
) {
    suspend fun resolve(playlistId: String): List<PlaylistMixedTrack> {
        val localRows = localPlaylistDao.observePlaylistSongs(playlistId).first()
        val localSongIds = localRows.sortedBy { it.sortOrder }.map { it.songId }
        val localSongs = if (localSongIds.isEmpty()) {
            emptyList()
        } else {
            musicRepository.getSongsByIds(localSongIds).first()
        }
        val localById = localSongs.associateBy { it.id }
        val localTracks = localRows.mapNotNull { row ->
            localById[row.songId]?.let { song ->
                PlaylistMixedTrack.Local(song = song, sortOrder = row.sortOrder)
            }
        }

        val youtubeTracks = youTubePlaylistDao.observeForPlaylist(playlistId).first()
            .map { entity ->
                PlaylistMixedTrack.YouTube(
                    track = entity.toYouTubeTrack(),
                    sortOrder = entity.sortOrder,
                )
            }

        return (localTracks + youtubeTracks).sortedBy { it.sortOrder }
    }

    suspend fun nextSortOrder(playlistId: String): Int {
        val localMax = localPlaylistDao.getMaxSortOrder(playlistId)
        val youtubeMax = youTubePlaylistDao.getMaxSortOrder(playlistId)
        return maxOf(localMax, youtubeMax) + 1
    }
}

private fun PlaylistYouTubeTrackEntity.toYouTubeTrack(): YouTubeTrack = YouTubeTrack(
    videoId = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationMs = durationMs,
    displayTitle = displayTitle,
)
