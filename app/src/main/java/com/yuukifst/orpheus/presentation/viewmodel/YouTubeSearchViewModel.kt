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
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YouTubeSearchUiState(
    val query: String = "",
    val results: List<YouTubeTrack> = emptyList(),
    val searchHistory: List<SearchHistoryItem> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadingVideoIds: Set<String> = emptySet(),
    val snackbarMessage: String? = null,
)

@HiltViewModel
class YouTubeSearchViewModel @Inject constructor(
    private val searchRepository: YouTubeSearchRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val downloadRepository: YouTubeDownloadRepository,
    private val youTubePlaylistDao: YouTubePlaylistDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val mixedTrackResolver: PlaylistMixedTrackResolver,
    private val playbackController: YouTubePlaybackController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubeSearchUiState())
    val uiState: StateFlow<YouTubeSearchUiState> = _uiState.asStateFlow()

    init {
        refreshSearchHistory()
        viewModelScope.launch {
            playlistPreferencesRepository.userPlaylistsFlow.collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun search(query: String) {
        val trimmed = query.trim()
        _uiState.update { it.copy(query = trimmed, error = null) }
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                searchRepository.search(trimmed)
            }.onSuccess { results ->
                searchHistoryDao.insert(
                    SearchHistoryEntity(query = trimmed, timestamp = System.currentTimeMillis()),
                )
                refreshSearchHistory()
                _uiState.update { it.copy(results = results, isLoading = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Search failed",
                    )
                }
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
