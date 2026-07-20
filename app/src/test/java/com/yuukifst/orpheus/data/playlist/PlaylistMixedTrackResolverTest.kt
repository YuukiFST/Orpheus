package com.yuukifst.orpheus.data.playlist

import com.yuukifst.orpheus.data.database.LocalPlaylistDao
import com.yuukifst.orpheus.data.database.PlaylistSongEntity
import com.yuukifst.orpheus.data.database.PlaylistYouTubeTrackEntity
import com.yuukifst.orpheus.data.database.YouTubePlaylistDao
import com.yuukifst.orpheus.data.model.PlaylistMixedTrack
import com.yuukifst.orpheus.data.model.Song
import com.yuukifst.orpheus.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaylistMixedTrackResolverTest {
  private val localPlaylistDao = mockk<LocalPlaylistDao>()
  private val youTubePlaylistDao = mockk<YouTubePlaylistDao>()
  private val musicRepository = mockk<MusicRepository>()
  private val resolver = PlaylistMixedTrackResolver(
    localPlaylistDao = localPlaylistDao,
    youTubePlaylistDao = youTubePlaylistDao,
    musicRepository = musicRepository,
  )

  @Test
  fun resolve_mergesLocalAndYouTubeBySortOrder() = runTest {
    val playlistId = "pl-1"
    coEvery { localPlaylistDao.observePlaylistSongs(playlistId) } returns flowOf(
      listOf(
        PlaylistSongEntity(playlistId, "1", 0),
        PlaylistSongEntity(playlistId, "2", 2),
      ),
    )
    coEvery { youTubePlaylistDao.observeForPlaylist(playlistId) } returns flowOf(
      listOf(
        PlaylistYouTubeTrackEntity(
          playlistId = playlistId,
          videoId = "vid",
          sortOrder = 1,
          title = "YT",
          channelName = "Ch",
          thumbnailUrl = "",
          durationMs = 1000,
        ),
      ),
    )
    val localSong1 = Song.emptySong().copy(id = "1", title = "Local 1")
    val localSong2 = Song.emptySong().copy(id = "2", title = "Local 2")
    every { musicRepository.getSongsByIds(listOf("1", "2")) } returns flowOf(listOf(localSong1, localSong2))

    val tracks = resolver.resolve(playlistId)

    assertEquals(3, tracks.size)
    assertTrue(tracks[0] is PlaylistMixedTrack.Local)
    assertTrue(tracks[1] is PlaylistMixedTrack.YouTube)
    assertTrue(tracks[2] is PlaylistMixedTrack.Local)
  }

  @Test
  fun nextSortOrder_usesMaxAcrossTables() = runTest {
    coEvery { localPlaylistDao.getMaxSortOrder("pl") } returns 4
    coEvery { youTubePlaylistDao.getMaxSortOrder("pl") } returns 7
    assertEquals(8, resolver.nextSortOrder("pl"))
  }
}
