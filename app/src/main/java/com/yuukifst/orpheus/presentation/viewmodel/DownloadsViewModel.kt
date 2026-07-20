package com.yuukifst.orpheus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuukifst.orpheus.data.database.YouTubeDownloadDao
import com.yuukifst.orpheus.data.database.toYouTubeTrack
import com.yuukifst.orpheus.data.youtube.YouTubeDownloadRepository
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val downloads: List<YouTubeTrack> = emptyList(),
    val snackbarMessage: String? = null,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    downloadDao: YouTubeDownloadDao,
    private val downloadRepository: YouTubeDownloadRepository,
    private val playbackController: YouTubePlaybackController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    val downloads: StateFlow<List<YouTubeTrack>> = downloadDao.observeAll()
        .map { entities -> entities.map { it.toYouTubeTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            downloads.collect { tracks ->
                _uiState.update { it.copy(downloads = tracks) }
            }
        }
    }

    fun play(track: YouTubeTrack) {
        viewModelScope.launch {
            playbackController.playOnce(track)
        }
    }

    fun delete(videoId: String) {
        viewModelScope.launch {
            downloadRepository.delete(videoId)
            _uiState.update { it.copy(snackbarMessage = "Download deleted") }
        }
    }

    fun rename(videoId: String, displayTitle: String?) {
        viewModelScope.launch {
            downloadRepository.rename(videoId, displayTitle?.trim()?.takeIf { it.isNotBlank() })
            _uiState.update { it.copy(snackbarMessage = "Download renamed") }
        }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
