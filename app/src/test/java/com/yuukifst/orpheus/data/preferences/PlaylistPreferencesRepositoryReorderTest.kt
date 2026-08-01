package com.yuukifst.orpheus.data.preferences

import com.yuukifst.orpheus.data.database.LocalPlaylistDao
import com.yuukifst.orpheus.data.database.PlaylistEntity
import com.yuukifst.orpheus.data.database.PlaylistSongEntity
import com.yuukifst.orpheus.data.database.PlaylistWithSongsEntity
import com.yuukifst.orpheus.data.database.toEntity
import com.yuukifst.orpheus.data.model.Playlist
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaylistPreferencesRepositoryReorderTest {

    private val localPlaylistDao: LocalPlaylistDao = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)

    @Test
    fun `reorderPlaylists assigns dense display_order values`() = runTest {
        val playlistA = playlist(id = "a", displayOrder = 5)
        val playlistB = playlist(id = "b", displayOrder = 1)
        val playlistC = playlist(id = "c", displayOrder = 9)

        every { userPreferencesRepository.playlistsSortOptionFlow } returns flowOf("playlist_name_az")
        coEvery { localPlaylistDao.observePlaylistsWithSongs() } returns flowOf(
            listOf(
                row(playlistA),
                row(playlistB),
                row(playlistC),
            ),
        )
        coEvery { localPlaylistDao.getPlaylistCount() } returns 3

        val captured = mutableListOf<PlaylistEntity>()
        coEvery { localPlaylistDao.upsertPlaylist(capture(captured)) } returns Unit

        val repository = PlaylistPreferencesRepository(localPlaylistDao, userPreferencesRepository)
        repository.reorderPlaylists(listOf("c", "a", "b"))

        coVerify(exactly = 3) { localPlaylistDao.upsertPlaylist(any()) }
        assertEquals(
            listOf("c" to 0, "a" to 1, "b" to 2),
            captured.map { it.id to it.displayOrder },
        )
    }

    private fun playlist(id: String, displayOrder: Int): Playlist =
        Playlist(
            id = id,
            name = "Playlist $id",
            songIds = emptyList(),
            displayOrder = displayOrder,
        )

    private fun row(playlist: Playlist): PlaylistWithSongsEntity =
        PlaylistWithSongsEntity(
            playlist = playlist.toEntity(),
            songs = emptyList<PlaylistSongEntity>(),
        )
}
