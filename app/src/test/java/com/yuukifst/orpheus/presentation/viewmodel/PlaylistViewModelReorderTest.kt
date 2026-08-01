package com.yuukifst.orpheus.presentation.viewmodel

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.yuukifst.orpheus.MainCoroutineExtension
import com.yuukifst.orpheus.data.DailyMixManager
import com.yuukifst.orpheus.data.model.Playlist
import com.yuukifst.orpheus.data.model.SmartPlaylistRule
import com.yuukifst.orpheus.data.model.SortOption
import com.yuukifst.orpheus.data.model.toPlaylistSource
import com.yuukifst.orpheus.data.playlist.M3uManager
import com.yuukifst.orpheus.data.playlist.PlaylistMixedTrackResolver
import com.yuukifst.orpheus.data.playlist.PlaylistYouTubeMembership
import com.yuukifst.orpheus.data.preferences.PlaylistPreferencesRepository
import com.yuukifst.orpheus.data.repository.MusicRepository
import com.yuukifst.orpheus.data.youtube.YouTubeCachedTrackRepository
import com.yuukifst.orpheus.data.database.YouTubePlaylistDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class PlaylistViewModelReorderTest {

    private val playlistPreferencesRepository = mockk<PlaylistPreferencesRepository>(relaxed = true)
    private val musicRepository = mockk<MusicRepository>(relaxed = true)
    private val dailyMixManager = mockk<DailyMixManager>(relaxed = true)
    private val m3uManager = mockk<M3uManager>(relaxed = true)
    private val mixedTrackResolver = mockk<PlaylistMixedTrackResolver>(relaxed = true)
    private val youTubePlaylistDao = mockk<YouTubePlaylistDao>(relaxed = true)
    private val playlistYouTubeMembership = mockk<PlaylistYouTubeMembership>(relaxed = true)
    private val youTubeCachedTrackRepository = mockk<YouTubeCachedTrackRepository>(relaxed = true)
    private val playbackController = mockk<YouTubePlaybackController>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private lateinit var viewModel: PlaylistViewModel

    private val userPlaylistA = Playlist(id = "user-a", name = "A", songIds = emptyList(), displayOrder = 0)
    private val userPlaylistB = Playlist(id = "user-b", name = "B", songIds = emptyList(), displayOrder = 1)
    private val smartPlaylist = Playlist(
        id = "smart-1",
        name = "Top Played",
        songIds = emptyList(),
        source = SmartPlaylistRule.TOP_PLAYED.toPlaylistSource(),
    )

    @BeforeEach
    fun setUp() {
        every { playlistPreferencesRepository.playlistSongOrderModesFlow } returns flowOf(emptyMap())
        every { playlistPreferencesRepository.playlistsSortOptionFlow } returns flowOf(
            SortOption.PlaylistNameAZ.storageKey,
        )
        every { playlistPreferencesRepository.userPlaylistsFlow } returns flowOf(emptyList())

        viewModel = PlaylistViewModel(
            playlistPreferencesRepository = playlistPreferencesRepository,
            musicRepository = musicRepository,
            dailyMixManager = dailyMixManager,
            m3uManager = m3uManager,
            mixedTrackResolver = mixedTrackResolver,
            youTubePlaylistDao = youTubePlaylistDao,
            playlistYouTubeMembership = playlistYouTubeMembership,
            youTubeCachedTrackRepository = youTubeCachedTrackRepository,
            playbackController = playbackController,
            context = context,
        )
    }

    @Test
    fun reorderPlaylists_setsManualSortPersistsOrderAndKeepsSmartPlaylistsLast() = runTest {
        seedPlaylists(listOf(userPlaylistA, userPlaylistB, smartPlaylist))
        coEvery { playlistPreferencesRepository.reorderPlaylists(any()) } returns Unit
        coEvery { playlistPreferencesRepository.setPlaylistsSortOption(any()) } returns Unit

        viewModel.reorderPlaylists(listOf(userPlaylistB.id, userPlaylistA.id))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.currentPlaylistSortOption).isEqualTo(SortOption.PlaylistManual)
        assertThat(viewModel.uiState.value.playlists.map { it.id })
            .containsExactly(userPlaylistB.id, userPlaylistA.id, smartPlaylist.id)
            .inOrder()

        coVerify(exactly = 1) {
            playlistPreferencesRepository.reorderPlaylists(listOf(userPlaylistB.id, userPlaylistA.id))
        }
        coVerify(exactly = 1) {
            playlistPreferencesRepository.setPlaylistsSortOption(SortOption.PlaylistManual.storageKey)
        }
    }

    private fun seedPlaylists(playlists: List<Playlist>) {
        val field = PlaylistViewModel::class.java.getDeclaredField("_uiState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(viewModel) as MutableStateFlow<PlaylistUiState>
        stateFlow.value = stateFlow.value.copy(
            playlists = playlists.toImmutableList(),
            currentPlaylistSortOption = SortOption.PlaylistNameAZ,
        )
    }
}
