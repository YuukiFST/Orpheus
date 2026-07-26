package com.yuukifst.orpheus.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.yuukifst.orpheus.R
import com.yuukifst.orpheus.data.backup.model.PlaylistConflict
import com.yuukifst.orpheus.data.backup.model.PlaylistConflictAction
import com.yuukifst.orpheus.data.backup.model.PlaylistConflictMatchReason
import com.yuukifst.orpheus.ui.theme.OrpheusFilledIconButton
import com.yuukifst.orpheus.ui.theme.RoundedSans
import com.yuukifst.orpheus.ui.theme.TerminalCornerShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlaylistConflictResolveDialog(
    conflicts: List<PlaylistConflict>,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onConfirm: (Map<String, PlaylistConflictAction>) -> Unit
) {
    val decisions = remember(conflicts) {
        mutableStateMapOf<String, PlaylistConflictAction>()
    }
    val allChosen = conflicts.isNotEmpty() && conflicts.all { it.backupPlaylistId in decisions }

    Dialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !inProgress,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f),
            shape = TerminalCornerShape,
            color = MaterialTheme.colorScheme.surface
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.backup_playlist_conflicts_title),
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = RoundedSans),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            OrpheusFilledIconButton(
                                onClick = onBack,
                                enabled = !inProgress
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = null
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!allChosen) {
                            Text(
                                text = stringResource(R.string.backup_playlist_conflicts_choose_all),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { onConfirm(decisions.toMap()) },
                                enabled = allChosen && !inProgress
                            ) {
                                Text(stringResource(R.string.backup_playlist_conflicts_continue))
                            }
                        }
                    }
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.backup_playlist_conflicts_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(conflicts, key = { it.backupPlaylistId }) { conflict ->
                        PlaylistConflictCard(
                            conflict = conflict,
                            selected = decisions[conflict.backupPlaylistId],
                            enabled = !inProgress,
                            onSelect = { action ->
                                decisions[conflict.backupPlaylistId] = action
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistConflictCard(
    conflict: PlaylistConflict,
    selected: PlaylistConflictAction?,
    enabled: Boolean,
    onSelect: (PlaylistConflictAction) -> Unit
) {
    Surface(
        shape = TerminalCornerShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = conflict.backupPlaylistName.ifBlank { conflict.backupPlaylistId },
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = RoundedSans),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    R.string.backup_playlist_conflict_device_label,
                    conflict.devicePlaylistName
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.backup_playlist_conflict_backup_label,
                    conflict.backupPlaylistName
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when (conflict.matchReason) {
                    PlaylistConflictMatchReason.ID ->
                        stringResource(R.string.backup_playlist_conflict_match_id)
                    PlaylistConflictMatchReason.NAME ->
                        stringResource(R.string.backup_playlist_conflict_match_name)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConflictActionChip(
                    label = stringResource(R.string.backup_playlist_action_merge),
                    selected = selected == PlaylistConflictAction.MERGE,
                    enabled = enabled,
                    onClick = { onSelect(PlaylistConflictAction.MERGE) }
                )
                ConflictActionChip(
                    label = stringResource(R.string.backup_playlist_action_replace),
                    selected = selected == PlaylistConflictAction.REPLACE,
                    enabled = enabled,
                    onClick = { onSelect(PlaylistConflictAction.REPLACE) }
                )
                ConflictActionChip(
                    label = stringResource(R.string.backup_playlist_action_ignore),
                    selected = selected == PlaylistConflictAction.IGNORE,
                    enabled = enabled,
                    onClick = { onSelect(PlaylistConflictAction.IGNORE) }
                )
            }
        }
    }
}

@Composable
private fun ConflictActionChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) }
    )
}
