package com.yuukifst.orpheus.presentation.screens
import com.yuukifst.orpheus.ui.theme.OrpheusTextButton
import com.yuukifst.orpheus.ui.theme.TerminalCornerShape

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)

    LaunchedEffect(Unit) {
        onSearchBarActiveChange(false)
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
                    .padding(start = 24.dp, top = statusBarTopInset + 12.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                            leadingIcon = {},
                            trailingIcon = {},
                            colors = SearchBarDefaults.inputFieldColors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier.fillMaxWidth(),
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = bottomBarHeightDp + 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.results, key = { it.videoId }) { track ->
                            YouTubeSearchResultItem(
                                track = track,
                                isDownloading = track.videoId in uiState.downloadingVideoIds,
                                onPlayOnce = { viewModel.playOnce(track) },
                                onAddToPlaylist = { showPlaylistPickerForTrack = track },
                                onDownload = { viewModel.download(track) },
                            )
                        }
                    }
                }
                uiState.suggestions.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = bottomBarHeightDp + 16.dp,
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
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
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
                            .padding(horizontal = 24.dp),
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
                        bottomPadding = bottomBarHeightDp + 16.dp,
                        onHistoryClick = { query ->
                            searchQuery = query
                            viewModel.updateQuery(query)
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
                .padding(horizontal = 24.dp, vertical = 12.dp),
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
                start = 16.dp,
                end = 16.dp,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(historyItems, key = { "history_${it.id ?: it.query}" }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(item.query) {
                            detectTapGestures(onTap = { onHistoryClick(item.query) })
                        }
                        .padding(horizontal = 8.dp, vertical = 12.dp),
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

@Composable
private fun YouTubeSearchResultItem(
    track: YouTubeTrack,
    isDownloading: Boolean,
    onPlayOnce: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TerminalCornerShape)
            .clickable(onClick = onPlayOnce)
            .padding(8.dp),
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
                    text = { Text("Play once") },
                    onClick = {
                        showMenu = false
                        onPlayOnce()
                    },
                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
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
