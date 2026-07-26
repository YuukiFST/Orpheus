package com.yuukifst.orpheus.presentation.screens
import com.yuukifst.orpheus.ui.theme.LocalTerminalChrome
import com.yuukifst.orpheus.ui.theme.OrpheusSearchBarShape
import com.yuukifst.orpheus.ui.theme.OrpheusSpacing
import com.yuukifst.orpheus.ui.theme.OrpheusTextButton
import com.yuukifst.orpheus.ui.theme.TerminalCornerShape
import com.yuukifst.orpheus.ui.theme.terminalBorder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuukifst.orpheus.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuukifst.orpheus.data.model.SearchHistoryItem
import com.yuukifst.orpheus.data.model.isSmartPlaylist
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import com.yuukifst.orpheus.presentation.components.SmartImage
import com.yuukifst.orpheus.presentation.components.SmartImageYouTubeListTargetSize
import com.yuukifst.orpheus.presentation.components.resolveNavBarOccupiedHeight
import com.yuukifst.orpheus.presentation.viewmodel.PlayerViewModel
import com.yuukifst.orpheus.presentation.viewmodel.YouTubeSearchViewModel
import com.yuukifst.orpheus.presentation.viewmodel.toPlaybackSong
import com.yuukifst.orpheus.utils.formatDuration

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YouTubeSearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: YouTubeSearchViewModel = hiltViewModel(),
    onSearchBarActiveChange: (Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf(uiState.query) }
    var showPlaylistPickerForTrack by remember { mutableStateOf<YouTubeTrack?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)

    LaunchedEffect(Unit) {
        onSearchBarActiveChange(false)
        viewModel.warmUpConnection()
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbarMessage()
        }
    }

    LaunchedEffect(Unit) {
        playerViewModel.youTubeChannelSearchRequests.collect { channelName ->
            if (channelName.isBlank()) return@collect
            searchQuery = channelName
            viewModel.searchChannel(channelName)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = OrpheusSpacing.lg,
                        top = statusBarTopInset + OrpheusSpacing.sm,
                        end = OrpheusSpacing.lg,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val searchBarShape = OrpheusSearchBarShape
                val showTerminalChrome = LocalTerminalChrome.current
                DockedSearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = searchQuery,
                            onQueryChange = {
                                searchQuery = it
                                viewModel.updateQuery(it)
                            },
                            onSearch = { viewModel.search(searchQuery) },
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = { Text("Search YouTube") },
                            leadingIcon = {
                                Text(
                                    text = ">",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            },
                            trailingIcon = {},
                            colors = SearchBarDefaults.inputFieldColors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (showTerminalChrome) {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.outline, searchBarShape)
                            } else {
                                Modifier
                            }
                        )
                        .clip(searchBarShape),
                    shape = searchBarShape,
                    colors = SearchBarDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    ),
                ) {}
            }

            when {
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = uiState.error ?: "Search failed",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                uiState.results.isNotEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (uiState.isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = OrpheusSpacing.lg,
                                        vertical = OrpheusSpacing.xs,
                                    ),
                            )
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (uiState.isLoading) {
                                        Modifier.alpha(0.55f)
                                    } else {
                                        Modifier
                                    },
                                ),
                        contentPadding = PaddingValues(
                            start = OrpheusSpacing.lg,
                            end = OrpheusSpacing.lg,
                            top = OrpheusSpacing.sm,
                            bottom = bottomBarHeightDp + OrpheusSpacing.md,
                        ),
                        verticalArrangement = Arrangement.spacedBy(OrpheusSpacing.xs),
                    ) {
                        items(uiState.results, key = { it.videoId }) { track ->
                            YouTubeSearchResultItem(
                                track = track,
                                isDownloading = track.videoId in uiState.downloadingVideoIds,
                                isFavorite = track.mediaId in favoriteSongIds,
                                onPlay = { viewModel.playOnce(track) },
                                onToggleFavorite = {
                                    playerViewModel.toggleFavoriteSpecificSong(track.toPlaybackSong())
                                },
                                onAddToQueue = { viewModel.addToQueue(track) },
                                onAddToPlaylist = { showPlaylistPickerForTrack = track },
                                onDownload = { viewModel.download(track) },
                                onChannelClick = { channelName ->
                                    searchQuery = channelName
                                    viewModel.searchChannel(channelName)
                                },
                            )
                        }
                    }
                    }
                }
                uiState.suggestions.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = OrpheusSpacing.lg,
                            end = OrpheusSpacing.lg,
                            top = OrpheusSpacing.sm,
                            bottom = bottomBarHeightDp + OrpheusSpacing.md,
                        ),
                    ) {
                        if (uiState.isLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                        items(uiState.suggestions, key = { it }) { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = suggestion
                                        viewModel.searchSuggestion(suggestion)
                                    }
                                    .padding(horizontal = OrpheusSpacing.xs, vertical = OrpheusSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.hasSearched -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = OrpheusSpacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No results for \"${uiState.query}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    YouTubeSearchHistorySection(
                        historyItems = uiState.searchHistory,
                        bottomPadding = bottomBarHeightDp + OrpheusSpacing.md,
                        onHistoryClick = { query ->
                            searchQuery = query
                            viewModel.search(query)
                        },
                        onHistoryDelete = viewModel::deleteSearchHistoryItem,
                        onClearAllHistory = viewModel::clearSearchHistory,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomBarHeightDp),
        )
    }

    showPlaylistPickerForTrack?.let { track ->
        YouTubePlaylistPickerSheet(
            playlists = uiState.playlists.filterNot { it.isSmartPlaylist },
            onDismiss = { showPlaylistPickerForTrack = null },
            onPlaylistSelected = { playlistId ->
                viewModel.addToPlaylist(track, playlistId)
                showPlaylistPickerForTrack = null
            },
        )
    }
}

@Composable
private fun YouTubeSearchHistorySection(
    historyItems: List<SearchHistoryItem>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onClearAllHistory: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OrpheusSpacing.lg, vertical = OrpheusSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            if (historyItems.isNotEmpty()) {
                OrpheusTextButton(onClick = onClearAllHistory) {
                    Text("Clear all")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = OrpheusSpacing.lg,
                end = OrpheusSpacing.lg,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(OrpheusSpacing.xxs),
        ) {
            items(historyItems, key = { "history_${it.id ?: it.query}" }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(item.query) {
                            detectTapGestures(onTap = { onHistoryClick(item.query) })
                        }
                        .padding(horizontal = OrpheusSpacing.xs, vertical = OrpheusSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item.query,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onHistoryDelete(item.query) }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = "Delete search history item",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeSearchResultItem(
    track: YouTubeTrack,
    isDownloading: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onChannelClick: (String) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onPlay,
        shape = TerminalCornerShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .terminalBorder(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(OrpheusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        SmartImage(
            model = track.thumbnailUrl,
            contentDescription = track.effectiveTitle,
            modifier = Modifier
                .size(72.dp)
                .clip(TerminalCornerShape),
            targetSize = SmartImageYouTubeListTargetSize,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.effectiveTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = track.channelName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    onChannelClick(track.channelName)
                },
            )
            if (track.durationMs > 0L) {
                Text(
                    text = formatDuration(track.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More actions")
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (isFavorite) {
                                    R.string.cd_remove_from_favorites
                                } else {
                                    R.string.cd_add_to_favorites
                                },
                            ),
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleFavorite()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = null,
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_add_to_queue)) },
                    onClick = {
                        showMenu = false
                        onAddToQueue()
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    onClick = {
                        showMenu = false
                        onAddToPlaylist()
                    },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Download") },
                    onClick = {
                        showMenu = false
                        onDownload()
                    },
                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                )
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubePlaylistPickerSheet(
    playlists: List<com.yuukifst.orpheus.data.model.Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Add to playlist",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (playlists.isEmpty()) {
                Text(
                    text = "No playlists available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            } else {
                playlists.forEach { playlist ->
                    Text(
                        text = playlist.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistSelected(playlist.id) }
                            .padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
