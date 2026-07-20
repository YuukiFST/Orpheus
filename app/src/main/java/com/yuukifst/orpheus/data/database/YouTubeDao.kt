package com.yuukifst.orpheus.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface YouTubeDownloadDao {
    @Query("SELECT * FROM youtube_downloads ORDER BY downloaded_at DESC")
    fun observeAll(): Flow<List<YouTubeDownloadEntity>>

    @Query("SELECT * FROM youtube_downloads WHERE video_id = :videoId LIMIT 1")
    suspend fun getByVideoId(videoId: String): YouTubeDownloadEntity?

    @Query("SELECT * FROM youtube_downloads WHERE video_id = :videoId LIMIT 1")
    fun observeByVideoId(videoId: String): Flow<YouTubeDownloadEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: YouTubeDownloadEntity)

    @Query("DELETE FROM youtube_downloads WHERE video_id = :videoId")
    suspend fun delete(videoId: String)

    @Query("UPDATE youtube_downloads SET display_title = :displayTitle WHERE video_id = :videoId")
    suspend fun updateDisplayTitle(videoId: String, displayTitle: String?)
}

@Dao
interface YouTubePlaylistDao {
    @Query("SELECT * FROM playlist_youtube_tracks WHERE playlist_id = :playlistId ORDER BY sort_order ASC")
    fun observeForPlaylist(playlistId: String): Flow<List<PlaylistYouTubeTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PlaylistYouTubeTrackEntity>)

    @Query("DELETE FROM playlist_youtube_tracks WHERE playlist_id = :playlistId")
    suspend fun clearForPlaylist(playlistId: String)

    @Query("DELETE FROM playlist_youtube_tracks WHERE playlist_id = :playlistId AND video_id = :videoId")
    suspend fun removeTrack(playlistId: String, videoId: String)

    @Query("UPDATE playlist_youtube_tracks SET display_title = :displayTitle WHERE playlist_id = :playlistId AND video_id = :videoId")
    suspend fun updateDisplayTitle(playlistId: String, videoId: String, displayTitle: String?)

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM playlist_youtube_tracks WHERE playlist_id = :playlistId")
    suspend fun getMaxSortOrder(playlistId: String): Int

    @Transaction
    suspend fun replaceForPlaylist(playlistId: String, tracks: List<PlaylistYouTubeTrackEntity>) {
        clearForPlaylist(playlistId)
        if (tracks.isNotEmpty()) upsertAll(tracks)
    }
}
