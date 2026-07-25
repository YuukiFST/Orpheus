package com.yuukifst.orpheus.presentation.viewmodel

import androidx.compose.runtime.Immutable
import com.yuukifst.orpheus.data.model.FolderSource
import com.yuukifst.orpheus.data.model.MusicFolder
import com.yuukifst.orpheus.data.model.SearchFilterType
import com.yuukifst.orpheus.data.model.SearchHistoryItem
import com.yuukifst.orpheus.data.model.SearchResultItem
import com.yuukifst.orpheus.data.model.Song
import com.yuukifst.orpheus.data.model.SortOption
import com.yuukifst.orpheus.data.model.StorageFilter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class QueueUiState(
    val currentPlaybackQueue: ImmutableList<Song> = persistentListOf(),
    val currentQueueSourceName: String = "All Songs",
    val preparingSongId: String? = null,
    val showDismissUndoBar: Boolean = false,
    val dismissedSong: Song? = null,
    val dismissedQueue: ImmutableList<Song> = persistentListOf(),
    val dismissedQueueName: String = "",
    val dismissedPosition: Long = 0L,
    val showQueueItemUndoBar: Boolean = false,
    val lastRemovedQueueSong: Song? = null,
    val lastRemovedQueueIndex: Int = -1,
    val undoBarVisibleDuration: Long = 4000L,
)

@Immutable
data class SearchUiState(
    val searchResults: ImmutableList<SearchResultItem> = persistentListOf(),
    val filteredSongs: ImmutableList<Song> = persistentListOf(),
    val isFiltering: Boolean = false,
    val searchHistory: ImmutableList<SearchHistoryItem> = persistentListOf(),
    val searchQuery: String = "",
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
)

@Immutable
data class LibraryPrefsUiState(
    val currentFolder: MusicFolder? = null,
    val folderSourceRootPath: String = "",
    val folderSource: FolderSource = FolderSource.INTERNAL,
    val isFoldersPlaylistView: Boolean = false,
    val currentStorageFilter: StorageFilter = StorageFilter.ALL,
    val currentSongSortOption: SortOption = SortOption.SongTitleAZ,
    val currentAlbumSortOption: SortOption = SortOption.AlbumTitleAZ,
    val currentArtistSortOption: SortOption = SortOption.ArtistNameAZ,
    val currentFavoriteSortOption: SortOption = SortOption.LikedSongDateLiked,
    val currentFolderSortOption: SortOption = SortOption.FolderNameAZ,
    val isAlbumsListView: Boolean = false,
    val isSdCardAvailable: Boolean = false,
    val musicFolders: ImmutableList<MusicFolder> = persistentListOf(),
    val isLoadingLibraryCategories: Boolean = true,
    val isSyncingLibrary: Boolean = false,
    val isLoadingInitialSongs: Boolean = true,
    val hideLocalMedia: Boolean = false,
    val folderBackGestureNavigationEnabled: Boolean = true,
)

fun PlayerUiState.toQueueUiState(): QueueUiState = QueueUiState(
    currentPlaybackQueue = currentPlaybackQueue,
    currentQueueSourceName = currentQueueSourceName,
    preparingSongId = preparingSongId,
    showDismissUndoBar = showDismissUndoBar,
    dismissedSong = dismissedSong,
    dismissedQueue = dismissedQueue,
    dismissedQueueName = dismissedQueueName,
    dismissedPosition = dismissedPosition,
    showQueueItemUndoBar = showQueueItemUndoBar,
    lastRemovedQueueSong = lastRemovedQueueSong,
    lastRemovedQueueIndex = lastRemovedQueueIndex,
    undoBarVisibleDuration = undoBarVisibleDuration,
)

fun PlayerUiState.toSearchUiState(): SearchUiState = SearchUiState(
    searchResults = searchResults,
    filteredSongs = filteredSongs,
    isFiltering = isFiltering,
    searchHistory = searchHistory,
    searchQuery = searchQuery,
    selectedSearchFilter = selectedSearchFilter,
)

fun PlayerUiState.toLibraryPrefsUiState(): LibraryPrefsUiState = LibraryPrefsUiState(
    currentFolder = currentFolder,
    folderSourceRootPath = folderSourceRootPath,
    folderSource = folderSource,
    isFoldersPlaylistView = isFoldersPlaylistView,
    currentStorageFilter = currentStorageFilter,
    currentSongSortOption = currentSongSortOption,
    currentAlbumSortOption = currentAlbumSortOption,
    currentArtistSortOption = currentArtistSortOption,
    currentFavoriteSortOption = currentFavoriteSortOption,
    currentFolderSortOption = currentFolderSortOption,
    isAlbumsListView = isAlbumsListView,
    isSdCardAvailable = isSdCardAvailable,
    musicFolders = musicFolders,
    isLoadingLibraryCategories = isLoadingLibraryCategories,
    isSyncingLibrary = isSyncingLibrary,
    isLoadingInitialSongs = isLoadingInitialSongs,
    hideLocalMedia = hideLocalMedia,
    folderBackGestureNavigationEnabled = folderBackGestureNavigationEnabled,
)
