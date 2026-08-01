package com.yuukifst.orpheus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuukifst.orpheus.data.database.SearchHistoryDao
import com.yuukifst.orpheus.data.database.SearchHistoryEntity
import com.yuukifst.orpheus.data.database.toSearchHistoryItem
import com.yuukifst.orpheus.data.model.Playlist
import com.yuukifst.orpheus.data.model.SearchHistoryItem
import com.yuukifst.orpheus.data.playlist.PlaylistYouTubeMembership
import com.yuukifst.orpheus.data.preferences.PlaylistPreferencesRepository
import com.yuukifst.orpheus.data.youtube.YouTubeDownloadRepository
import com.yuukifst.orpheus.data.youtube.YouTubeSearchRepository
import com.yuukifst.orpheus.data.youtube.YouTubeStreamExtractor
import com.yuukifst.orpheus.data.youtube.YouTubeSuggestionRepository
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val playlistYouTubeMembership: PlaylistYouTubeMembership,
    private val playbackController: YouTubePlaybackController,
    private val streamExtractor: YouTubeStreamExtractor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubeSearchUiState())
    val uiState: StateFlow<YouTubeSearchUiState> = _uiState.asStateFlow()
    private var debouncedSearchJob: Job? = null
    private var debouncedSuggestionJob: Job? = null
    private var prefetchJob: Job? = null
    private val latestSearchRequestId = AtomicLong(0L)
    private var activeNetworkQuery: String? = null

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 260L
        const val SEARCH_DEBOUNCE_CACHED_MS = 0L
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
        viewModelScope.launch {
            playbackController.playbackErrors.collect { message ->
                if (message.isNotBlank()) {
                    _uiState.update { it.copy(snackbarMessage = message) }
                }
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

        if (trimmed.length >= MIN_QUERY_LENGTH) {
            searchRepository.searchCachedOnly(trimmed)?.let { cached ->
                _uiState.update {
                    it.copy(
                        results = cached,
                        isLoading = false,
                        error = null,
                        hasSearched = true,
                        suggestions = emptyList(),
                    )
                }
            }
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
            val cachedAlready = searchRepository.searchCachedOnly(trimmed) != null
            delay(if (cachedAlready) SEARCH_DEBOUNCE_CACHED_MS else SEARCH_DEBOUNCE_MS)
            if (trimmed.length < MIN_QUERY_LENGTH) {
                _uiState.update { it.copy(results = emptyList(), isLoading = false, hasSearched = false) }
                return@launch
            }
            executeSearch(trimmed, saveHistory = true)
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

    fun warmUpConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            searchRepository.warmUpConnection()
        }
    }

    private suspend fun executeSearch(trimmed: String, saveHistory: Boolean) {
        val requestId = latestSearchRequestId.incrementAndGet()

        searchRepository.searchCachedOnly(trimmed)?.let { cached ->
            if (requestId != latestSearchRequestId.get()) return
            _uiState.update {
                it.copy(
                    results = cached,
                    isLoading = false,
                    error = null,
                    hasSearched = true,
                    suggestions = emptyList(),
                )
            }
            prefetchTopResult(cached)
            if (saveHistory) {
                persistSearchHistory(trimmed)
            }
            return
        }

        if (activeNetworkQuery != null && activeNetworkQuery != trimmed) {
            searchRepository.cancelActiveRequest()
        }
        activeNetworkQuery = trimmed
        _uiState.update { it.copy(isLoading = true, error = null, hasSearched = true) }
        try {
            val results = searchRepository.search(trimmed)
            if (requestId != latestSearchRequestId.get()) return
            _uiState.update {
                it.copy(results = results, isLoading = false, suggestions = emptyList())
            }
            prefetchTopResult(results)
            if (saveHistory) {
                persistSearchHistory(trimmed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (error: Exception) {
            if (requestId != latestSearchRequestId.get()) return
            val message = error.message.orEmpty()
            if (message.contains("cancel", ignoreCase = true)) {
                _uiState.update { it.copy(isLoading = false) }
                return
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = message.ifBlank { "Search failed" },
                )
            }
        } finally {
            if (activeNetworkQuery == trimmed) {
                activeNetworkQuery = null
            }
        }
    }

    private fun prefetchTopResult(results: List<YouTubeTrack>) {
        val videoId = results.firstOrNull()?.videoId ?: return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            streamExtractor.prefetchBestAudio(videoId)
        }
    }

    private fun persistSearchHistory(trimmed: String) {
        viewModelScope.launch(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(trimmed)
            searchHistoryDao.insert(
                SearchHistoryEntity(query = trimmed, timestamp = System.currentTimeMillis()),
            )
            refreshSearchHistory()
        }
    }

    fun searchChannel(channelName: String) {
        search(channelName)
    }

    fun playOnce(track: YouTubeTrack) {
        viewModelScope.launch {
            playbackController.playOnce(track)
        }
    }

    fun addToQueue(track: YouTubeTrack) {
        viewModelScope.launch {
            playbackController.addToQueue(track)
        }
    }

    fun addToPlaylist(track: YouTubeTrack, playlistId: String) {
        viewModelScope.launch {
            addToPlaylistInternal(track, playlistId)
        }
    }

    fun createPlaylistAndAdd(track: YouTubeTrack, name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@launch
            val playlist = playlistPreferencesRepository.createPlaylist(trimmed, emptyList())
            addToPlaylistInternal(track, playlist.id, playlist.name)
        }
    }

    private suspend fun addToPlaylistInternal(
        track: YouTubeTrack,
        playlistId: String,
        playlistName: String? = null,
    ) {
        if (playlistId in playlistYouTubeMembership.playlistIdsContainingVideo(track.videoId)) {
            _uiState.update { it.copy(snackbarMessage = "Already in playlist") }
            return
        }
        playlistYouTubeMembership.addYouTubeTrackToPlaylist(playlistId, track)
        val resolvedName = playlistName
            ?: _uiState.value.playlists.find { it.id == playlistId }?.name
            ?: "playlist"
        _uiState.update { it.copy(snackbarMessage = "Added to $resolvedName") }
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
