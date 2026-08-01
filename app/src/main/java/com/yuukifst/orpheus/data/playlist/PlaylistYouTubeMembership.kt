package com.yuukifst.orpheus.data.playlist

import com.yuukifst.orpheus.data.database.LocalPlaylistDao
import com.yuukifst.orpheus.data.database.PlaylistYouTubeTrackEntity
import com.yuukifst.orpheus.data.database.YouTubePlaylistDao
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import com.yuukifst.orpheus.utils.isYouTubeMediaId
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistYouTubeMembership @Inject constructor(
    private val localPlaylistDao: LocalPlaylistDao,
    private val youTubePlaylistDao: YouTubePlaylistDao,
    private val mixedTrackResolver: PlaylistMixedTrackResolver,
) {
    suspend fun addYouTubeTrackToPlaylist(playlistId: String, track: YouTubeTrack) {
        val existing = youTubePlaylistDao.observeForPlaylist(playlistId).first()
        if (existing.any { it.videoId == track.videoId }) return

        val sortOrder = mixedTrackResolver.nextSortOrder(playlistId)
        val entity = PlaylistYouTubeTrackEntity(
            playlistId = playlistId,
            videoId = track.videoId,
            sortOrder = sortOrder,
            title = track.title,
            channelName = track.channelName,
            thumbnailUrl = track.thumbnailUrl,
            durationMs = track.durationMs,
            displayTitle = track.displayTitle,
        )
        youTubePlaylistDao.upsertAll(listOf(entity))
    }

    suspend fun playlistIdsContainingVideo(videoId: String): Set<String> =
        youTubePlaylistDao.getPlaylistIdsContainingVideo(videoId).toSet()

    suspend fun applyMixedOrder(playlistId: String, orderedMediaIds: List<String>) {
        val localBySongId = localPlaylistDao.observePlaylistSongs(playlistId).first()
            .associateBy { it.songId }
        val youtubeByVideoId = youTubePlaylistDao.observeForPlaylist(playlistId).first()
            .associateBy { it.videoId }

        val localWithOrder = mutableListOf<Pair<String, Int>>()
        val youtubeWithOrder = mutableListOf<PlaylistYouTubeTrackEntity>()

        orderedMediaIds.forEachIndexed { index, mediaId ->
            if (mediaId.isYouTubeMediaId()) {
                val videoId = mediaId.removePrefix("youtube_")
                youtubeByVideoId[videoId]?.let { entity ->
                    youtubeWithOrder.add(entity.copy(sortOrder = index))
                }
            } else if (localBySongId.containsKey(mediaId)) {
                localWithOrder.add(mediaId to index)
            }
        }

        localPlaylistDao.replacePlaylistSongsWithOrder(playlistId, localWithOrder)
        youTubePlaylistDao.replaceForPlaylist(playlistId, youtubeWithOrder)
    }
}
