package com.yuukifst.orpheus.presentation.screens
import com.yuukifst.orpheus.ui.theme.OrpheusTextButton
import com.yuukifst.orpheus.ui.theme.TerminalCornerShape

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import com.yuukifst.orpheus.presentation.components.SmartImage
import com.yuukifst.orpheus.presentation.components.SmartImageYouTubeListTargetSize
import com.yuukifst.orpheus.presentation.components.resolveNavBarOccupiedHeight
import com.yuukifst.orpheus.presentation.viewmodel.DownloadsViewModel
import com.yuukifst.orpheus.presentation.viewmodel.PlayerViewModel
import com.yuukifst.orpheus.utils.formatDuration

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DownloadsScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var trackToRename by remember { mutableStateOf<YouTubeTrack?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbarMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = statusBarTopInset + 16.dp,
                    bottom = 12.dp,
                ),
            )

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No downloads yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = bottomBarHeightDp + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(downloads, key = { it.videoId }) { track ->
                        DownloadedTrackItem(
                            track = track,
                            onPlay = { viewModel.play(track) },
                            onRename = { trackToRename = track },
                            onDelete = { viewModel.delete(track.videoId) },
                        )
                    }
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

    trackToRename?.let { track ->
        RenameDownloadDialog(
            initialTitle = track.effectiveTitle,
            onDismiss = { trackToRename = null },
            onConfirm = { newTitle ->
                viewModel.rename(track.videoId, newTitle)
                trackToRename = null
            },
        )
    }
}

@Composable
private fun DownloadedTrackItem(
    track: YouTubeTrack,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TerminalCornerShape)
            .clickable(onClick = onPlay)
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
                Icon(Icons.Rounded.MoreVert, contentDescription = "More actions")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Play") },
                    onClick = {
                        showMenu = false
                        onPlay()
                    },
                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        showMenu = false
                        onRename()
                    },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun RenameDownloadDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename download") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            OrpheusTextButton(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OrpheusTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
