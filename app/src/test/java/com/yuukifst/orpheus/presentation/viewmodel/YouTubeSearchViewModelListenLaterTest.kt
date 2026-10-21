package com.yuukifst.orpheus.presentation.viewmodel

import com.yuukifst.orpheus.data.model.Playlist
import com.yuukifst.orpheus.data.playlist.PlaylistYouTubeMembership
import com.yuukifst.orpheus.data.preferences.PlaylistPreferencesRepository
import com.yuukifst.orpheus.data.preferences.UserPreferencesRepository
import com.yuukifst.orpheus.data.preferences.YouTubeResultLanguage
import com.yuukifst.orpheus.data.preferences.YouTubeSearchFilterPreferences
import com.yuukifst.orpheus.data.youtube.YouTubeSearchRepository
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeSearchViewModelListenLaterTest {

    private val searchRepository: YouTubeSearchRepository = mockk(relaxed = true)
    private val playlistPreferencesRepository: PlaylistPreferencesRepository = mockk(relaxed = true)
    private val playlistYouTubeMembership: PlaylistYouTubeMembership = mockk(relaxed = true)
    private val playbackController: YouTubePlaybackController = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val searchFilterPreferences: YouTubeSearchFilterPreferences = mockk(relaxed = true)

    private val podcastEpisode = YouTubeTrack(
        videoId = "podcast01",
        title = "The Daily Podcast - Episode 1",
        channelName = "The Daily",
        thumbnailUrl = "https://example.com/thumb.jpg",
        durationMs = 1_800_000L,
    )

    @BeforeEach
    fun setUp() {
        every { playlistPreferencesRepository.userPlaylistsFlow } returns flowOf(emptyList())
        every { playbackController.playbackErrors } returns MutableSharedFlow()
        every { userPreferencesRepository.youtubeSearchAsYouTypeFlow } returns flowOf(true)
        every { userPreferencesRepository.youtubeSearchDebounceMsFlow } returns flowOf(260)
        every { searchFilterPreferences.searchRegionFlow } returns flowOf("")
        every { searchFilterPreferences.resultLanguageFlow } returns flowOf(YouTubeResultLanguage.ANY)
        coEvery { playlistYouTubeMembership.playlistIdsContainingVideo(any()) } returns emptySet()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = YouTubeSearchViewModel(
        searchRepository = searchRepository,
        suggestionRepository = mockk(relaxed = true),
        searchHistoryDao = mockk(relaxed = true),
        downloadRepository = mockk(relaxed = true),
        playlistPreferencesRepository = playlistPreferencesRepository,
        playlistYouTubeMembership = playlistYouTubeMembership,
        playbackController = playbackController,
        streamExtractor = mockk(relaxed = true),
        userPreferencesRepository = userPreferencesRepository,
        searchFilterPreferences = searchFilterPreferences,
    )

    @Test
    fun `first save creates the Listen later playlist and adds the result`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns emptyList()
        coEvery {
            playlistPreferencesRepository.createPlaylist(name = "Listen later", customId = LISTEN_LATER_PLAYLIST_ID)
        } returns Playlist(id = LISTEN_LATER_PLAYLIST_ID, name = "Listen later", songIds = emptyList())
        val viewModel = createViewModel()

        viewModel.saveToListenLater(podcastEpisode, "Listen later")
        advanceUntilIdle()

        coVerify { playlistYouTubeMembership.addYouTubeTrackToPlaylist(LISTEN_LATER_PLAYLIST_ID, podcastEpisode) }
        assertEquals("Added to Listen later", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `save reuses a renamed Listen later playlist instead of creating another`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns listOf(
            Playlist(id = LISTEN_LATER_PLAYLIST_ID, name = "Podcasts queue", songIds = emptyList()),
        )
        val viewModel = createViewModel()

        viewModel.saveToListenLater(podcastEpisode, "Listen later")
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistPreferencesRepository.createPlaylist(any(), customId = any()) }
        coVerify { playlistYouTubeMembership.addYouTubeTrackToPlaylist(LISTEN_LATER_PLAYLIST_ID, podcastEpisode) }
        assertEquals("Added to Podcasts queue", viewModel.uiState.value.snackbarMessage)
    }

    @Test
    fun `saving a result already in Listen later does not add a duplicate`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { playlistPreferencesRepository.getPlaylistsOnce() } returns listOf(
            Playlist(id = LISTEN_LATER_PLAYLIST_ID, name = "Listen later", songIds = emptyList()),
        )
        coEvery { playlistYouTubeMembership.playlistIdsContainingVideo(podcastEpisode.videoId) } returns
            setOf(LISTEN_LATER_PLAYLIST_ID)
        val viewModel = createViewModel()

        viewModel.saveToListenLater(podcastEpisode, "Listen later")
        advanceUntilIdle()

        coVerify(exactly = 0) { playlistYouTubeMembership.addYouTubeTrackToPlaylist(any(), any()) }
        assertEquals("Already in playlist", viewModel.uiState.value.snackbarMessage)
    }
}
