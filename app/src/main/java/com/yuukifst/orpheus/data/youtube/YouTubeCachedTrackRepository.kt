package com.yuukifst.orpheus.data.youtube

import com.yuukifst.orpheus.data.database.YouTubeCachedTrackDao
import com.yuukifst.orpheus.data.database.mediaId
import com.yuukifst.orpheus.data.database.toCachedTrackEntity
import com.yuukifst.orpheus.data.database.YouTubeCachedTrackEntity
import com.yuukifst.orpheus.data.database.toSong
import com.yuukifst.orpheus.data.model.Song
import com.yuukifst.orpheus.data.model.SortOption
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeCachedTrackRepository @Inject constructor(
    private val dao: YouTubeCachedTrackDao,
) {
    suspend fun recordPlayed(track: YouTubeTrack) = withContext(Dispatchers.IO) {
        val existing = dao.getByVideoId(track.videoId)
        dao.upsert(
            track.toCachedTrackEntity(
                isFavorite = existing?.isFavorite ?: false,
                lastPlayedAt = System.currentTimeMillis(),
                favoritedAt = existing?.favoritedAt,
            ),
        )
    }

    suspend fun setFavorite(track: YouTubeTrack, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val existing = dao.getByVideoId(track.videoId)
        val now = System.currentTimeMillis()
        dao.upsert(
            track.toCachedTrackEntity(
                isFavorite = isFavorite,
                lastPlayedAt = existing?.lastPlayedAt ?: now,
                favoritedAt = if (isFavorite) now else null,
            ),
        )
    }

    suspend fun setFavoriteByMediaId(mediaId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val videoId = mediaId.removePrefix("youtube_")
        val existing = dao.getByVideoId(videoId) ?: return@withContext
        val now = System.currentTimeMillis()
        dao.upsert(
            existing.copy(
                isFavorite = isFavorite,
                favoritedAt = if (isFavorite) now else null,
            ),
        )
    }

    suspend fun setFavoriteFromSong(song: Song, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val videoId = song.id.removePrefix("youtube_")
        val existing = dao.getByVideoId(videoId)
        val now = System.currentTimeMillis()
        val track = YouTubeTrack(
            videoId = videoId,
            title = song.title,
            channelName = song.artist,
            thumbnailUrl = song.albumArtUriString.orEmpty(),
            durationMs = song.duration,
        )
        dao.upsert(
            track.toCachedTrackEntity(
                isFavorite = isFavorite,
                lastPlayedAt = existing?.lastPlayedAt ?: now,
                favoritedAt = if (isFavorite) now else null,
            ),
        )
    }

    suspend fun getSongsByMediaIds(mediaIds: List<String>): List<Song> = withContext(Dispatchers.IO) {
        if (mediaIds.isEmpty()) return@withContext emptyList()
        val videoIds = mediaIds.map { it.removePrefix("youtube_") }
        val byVideoId = dao.getByVideoIds(videoIds).associateBy { it.mediaId() }
        mediaIds.mapNotNull { byVideoId[it] }.map { it.toSong() }
    }

    fun observeSongsByMediaIds(mediaIds: List<String>): Flow<List<Song>> {
        if (mediaIds.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        val videoIds = mediaIds.map { it.removePrefix("youtube_") }
        return dao.observeByVideoIds(videoIds).map { entities ->
            val byMediaId = entities.associateBy { it.mediaId() }
            mediaIds.mapNotNull { byMediaId[it] }.map { it.toSong() }
        }
    }

    fun observeFavoriteMediaIds(): Flow<Set<String>> {
        return dao.observeFavoriteVideoIds()
            .map { ids -> ids.map { videoId -> "youtube_$videoId" }.toSet() }
    }

    suspend fun getFavoriteMediaIdsOnce(): Set<String> = withContext(Dispatchers.IO) {
        dao.getFavoriteVideoIdsOnce().map { videoId -> "youtube_$videoId" }.toSet()
    }

    fun observeFavoriteSongs(sortOption: SortOption): Flow<List<Song>> {
        return dao.observeFavoriteTracks().map { entities ->
            sortFavoriteEntities(entities, sortOption).map { it.toSong().copy(isFavorite = true) }
        }
    }

    suspend fun getFavoriteSongsOnce(sortOption: SortOption): List<Song> = withContext(Dispatchers.IO) {
        sortFavoriteEntities(dao.getFavoriteTracksOnce(), sortOption)
            .map { it.toSong().copy(isFavorite = true) }
    }

    private fun sortFavoriteEntities(
        entities: List<YouTubeCachedTrackEntity>,
        sortOption: SortOption,
    ): List<YouTubeCachedTrackEntity> {
        val titleCmp = compareBy<YouTubeCachedTrackEntity> {
            (it.displayTitle?.takeIf { title -> title.isNotBlank() } ?: it.title).lowercase()
        }
        val artistCmp = compareBy<YouTubeCachedTrackEntity> { it.channelName.lowercase() }
        val dateCmp = compareBy<YouTubeCachedTrackEntity> { it.favoritedAt ?: 0L }
        return when (sortOption) {
            SortOption.LikedSongTitleZA -> entities.sortedWith(titleCmp.reversed())
            SortOption.LikedSongArtist -> entities.sortedWith(artistCmp.then(titleCmp))
            SortOption.LikedSongArtistDesc -> entities.sortedWith(artistCmp.reversed().then(titleCmp))
            SortOption.LikedSongAlbum -> entities.sortedWith(titleCmp)
            SortOption.LikedSongAlbumDesc -> entities.sortedWith(titleCmp.reversed())
            SortOption.LikedSongDateLikedAsc -> entities.sortedWith(dateCmp)
            else -> entities.sortedWith(dateCmp.reversed())
        }
    }
}
