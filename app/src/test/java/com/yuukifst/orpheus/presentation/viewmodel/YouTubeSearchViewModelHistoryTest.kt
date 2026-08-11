package com.yuukifst.orpheus.presentation.viewmodel

import com.yuukifst.orpheus.data.database.SearchHistoryDao
import com.yuukifst.orpheus.data.playlist.PlaylistYouTubeMembership
import com.yuukifst.orpheus.data.preferences.PlaylistPreferencesRepository
import com.yuukifst.orpheus.data.youtube.YouTubeDownloadRepository
import com.yuukifst.orpheus.data.youtube.YouTubeSearchRepository
import com.yuukifst.orpheus.data.youtube.YouTubeStreamExtractor
import com.yuukifst.orpheus.data.youtube.YouTubeSuggestionRepository
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeSearchViewModelHistoryTest {

    private val searchRepository: YouTubeSearchRepository = mockk(relaxed = true)
    private val searchHistoryDao: SearchHistoryDao = mockk(relaxed = true)
    private val suggestionRepository: YouTubeSuggestionRepository = mockk(relaxed = true)
    private val downloadRepository: YouTubeDownloadRepository = mockk(relaxed = true)
    private val playlistPreferencesRepository: PlaylistPreferencesRepository = mockk(relaxed = true)
    private val playlistYouTubeMembership: PlaylistYouTubeMembership = mockk(relaxed = true)
    private val playbackController: YouTubePlaybackController = mockk(relaxed = true)
    private val streamExtractor: YouTubeStreamExtractor = mockk(relaxed = true)

    private val sampleTrack = YouTubeTrack(
        videoId = "dQw4w9WgXcQ",
        title = "Never Gonna Give You Up",
        channelName = "Rick Astley",
        thumbnailUrl = "https://example.com/thumb.jpg",
        durationMs = 212_000L,
    )

    @BeforeEach
    fun setUp() {
        every { playlistPreferencesRepository.userPlaylistsFlow } returns flowOf(emptyList())
        every { playbackController.playbackErrors } returns MutableSharedFlow()
        coEvery { searchHistoryDao.getRecentSearches(any()) } returns emptyList()
        coEvery { searchHistoryDao.deleteByQuery(any()) } just runs
        coEvery { searchHistoryDao.insert(any()) } just runs
        every { searchRepository.searchCachedOnly(any()) } returns null
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): YouTubeSearchViewModel {
        return YouTubeSearchViewModel(
            searchRepository = searchRepository,
            suggestionRepository = suggestionRepository,
            searchHistoryDao = searchHistoryDao,
            downloadRepository = downloadRepository,
            playlistPreferencesRepository = playlistPreferencesRepository,
            playlistYouTubeMembership = playlistYouTubeMembership,
            playbackController = playbackController,
            streamExtractor = streamExtractor,
        )
    }

    @Test
    fun `debounced typing does not persist incomplete query history`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        coEvery { searchRepository.search(any()) } returns listOf(sampleTrack)
        val viewModel = createViewModel()

        viewModel.updateQuery("ne")
        advanceTimeBy(260L)
        runCurrent()
        viewModel.updateQuery("nev")
        advanceTimeBy(260L)
        runCurrent()
        viewModel.updateQuery("never gonna")
        advanceTimeBy(260L)
        runCurrent()
        advanceUntilIdle()

        coVerify(exactly = 0) { searchHistoryDao.insert(any()) }
    }

    @Test
    fun `explicit search persists final query history`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val query = "never gonna"
        coEvery { searchRepository.search(query) } returns listOf(sampleTrack)
        val viewModel = createViewModel()

        viewModel.search(query)
        advanceUntilIdle()

        coVerify(timeout = 1_000) {
            searchHistoryDao.deleteByQuery(query)
            searchHistoryDao.insert(match { it.query == query })
        }
    }

    @Test
    fun `suggestion search persists history`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val query = "never gonna give"
        coEvery { searchRepository.search(query) } returns listOf(sampleTrack)
        val viewModel = createViewModel()

        viewModel.searchSuggestion(query)
        advanceUntilIdle()

        coVerify(timeout = 1_000) {
            searchHistoryDao.insert(match { it.query == query })
        }
    }

    @Test
    fun `debounced search does not persist blank query`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()

        viewModel.updateQuery("   ")
        advanceTimeBy(260L)
        advanceUntilIdle()

        coVerify(exactly = 0) { searchHistoryDao.insert(any()) }
    }
}
