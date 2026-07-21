package com.yuukifst.orpheus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuukifst.orpheus.data.database.PlaylistYouTubeTrackEntity
import com.yuukifst.orpheus.data.database.SearchHistoryDao
import com.yuukifst.orpheus.data.database.SearchHistoryEntity
import com.yuukifst.orpheus.data.database.YouTubePlaylistDao
import com.yuukifst.orpheus.data.database.toSearchHistoryItem
import com.yuukifst.orpheus.data.model.Playlist
import com.yuukifst.orpheus.data.model.SearchHistoryItem
import com.yuukifst.orpheus.data.playlist.PlaylistMixedTrackResolver
import com.yuukifst.orpheus.data.preferences.PlaylistPreferencesRepository
import com.yuukifst.orpheus.data.youtube.YouTubeDownloadRepository
import com.yuukifst.orpheus.data.youtube.YouTubeSearchRepository
import com.yuukifst.orpheus.data.youtube.YouTubeSuggestionRepository
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

data class YouTubeSearchUiState(
    val query: String = "",
    val results: List<YouTubeTrack> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val searchHistory: List<SearchHistoryItem> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
    val downloadingVideoIds: Set<String> = emptySet(),
    val snackbarMessage: String? = null,
)

@HiltViewModel
class YouTubeSearchViewModel @Inject constructor(
    private val searchRepository: YouTubeSearchRepository,
    private val suggestionRepository: YouTubeSuggestionRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val downloadRepository: YouTubeDownloadRepository,
    private val youTubePlaylistDao: YouTubePlaylistDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val mixedTrackResolver: PlaylistMixedTrackResolver,
    private val playbackController: YouTubePlaybackController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubeSearchUiState())
    val uiState: StateFlow<YouTubeSearchUiState> = _uiState.asStateFlow()
    private var debouncedSearchJob: Job? = null
    private var debouncedSuggestionJob: Job? = null
    private val latestSearchRequestId = AtomicLong(0L)

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 200L
        const val SUGGESTION_DEBOUNCE_MS = 150L
        const val MIN_QUERY_LENGTH = 2
    }

    init {
        refreshSearchHistory()
        viewModelScope.launch {
            playlistPreferencesRepository.userPlaylistsFlow.collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query, error = null) }
        debouncedSearchJob?.cancel()
        debouncedSuggestionJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    suggestions = emptyList(),
                    isLoading = false,
                    hasSearched = false,
                )
            }
            return
        }

        debouncedSuggestionJob = viewModelScope.launch {
            delay(SUGGESTION_DEBOUNCE_MS)
            if (trimmed.length < MIN_QUERY_LENGTH) {
                _uiState.update { it.copy(suggestions = emptyList()) }
                return@launch
            }
            val suggestions = suggestionRepository.suggestions(trimmed)
            _uiState.update { state ->
                if (state.query.trim() != trimmed) state else state.copy(suggestions = suggestions)
            }
        }

        debouncedSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            if (trimmed.length < MIN_QUERY_LENGTH) {
                _uiState.update { it.copy(results = emptyList(), isLoading = false, hasSearched = false) }
                return@launch
            }
            executeSearch(trimmed, saveHistory = false)
        }
    }

    fun search(query: String) {
        debouncedSearchJob?.cancel()
        debouncedSuggestionJob?.cancel()
        val trimmed = query.trim()
        _uiState.update { it.copy(query = trimmed, error = null) }
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    suggestions = emptyList(),
                    isLoading = false,
                    hasSearched = false,
                )
            }
            return
        }
        viewModelScope.launch {
            executeSearch(trimmed, saveHistory = true)
        }
    }

    fun searchSuggestion(text: String) {
        debouncedSearchJob?.cancel()
        debouncedSuggestionJob?.cancel()
        val trimmed = text.trim()
        _uiState.update { it.copy(query = trimmed, error = null) }
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            executeSearch(trimmed, saveHistory = true)
        }
    }

    private suspend fun executeSearch(trimmed: String, saveHistory: Boolean) {
        val requestId = latestSearchRequestId.incrementAndGet()
        _uiState.update { it.copy(isLoading = true, error = null, hasSearched = true) }
        try {
            val results = searchRepository.search(trimmed)
            if (requestId != latestSearchRequestId.get()) return
            if (saveHistory) {
                searchHistoryDao.deleteByQuery(trimmed)
                searchHistoryDao.insert(
                    SearchHistoryEntity(query = trimmed, timestamp = System.currentTimeMillis()),
                )
                refreshSearchHistory()
            }
            _uiState.update {
                it.copy(results = results, isLoading = false, suggestions = emptyList())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            if (requestId != latestSearchRequestId.get()) return
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = error.message ?: "Search failed",
                )
            }
        }
    }

    fun playOnce(track: YouTubeTrack) {
        viewModelScope.launch {
            playbackController.playOnce(track)
        }
    }

    fun addToPlaylist(track: YouTubeTrack, playlistId: String) {
        viewModelScope.launch {
            val existing = youTubePlaylistDao.observeForPlaylist(playlistId).first()
            if (existing.any { it.videoId == track.videoId }) {
                _uiState.update { it.copy(snackbarMessage = "Already in playlist") }
                return@launch
            }
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
            val playlistName = _uiState.value.playlists.find { it.id == playlistId }?.name ?: "playlist"
            _uiState.update { it.copy(snackbarMessage = "Added to $playlistName") }
        }
    }

    fun download(track: YouTubeTrack) {
        viewModelScope.launch {
            _uiState.update { it.copy(downloadingVideoIds = it.downloadingVideoIds + track.videoId) }
            val result = downloadRepository.download(track)
            _uiState.update { state ->
                state.copy(
                    downloadingVideoIds = state.downloadingVideoIds - track.videoId,
                    snackbarMessage = result.fold(
                        onSuccess = { "Downloaded ${it.effectiveTitle}" },
                        onFailure = { error -> error.message ?: "Download failed" },
                    ),
                )
            }
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch {
            searchHistoryDao.deleteByQuery(query)
            refreshSearchHistory()
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            searchHistoryDao.clearAll()
            refreshSearchHistory()
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private fun refreshSearchHistory() {
        viewModelScope.launch {
            val history = searchHistoryDao.getRecentSearches(20).map { it.toSearchHistoryItem() }
            _uiState.update { it.copy(searchHistory = history) }
        }
    }
}
