package com.yuukifst.orpheus.presentation.components

import com.yuukifst.orpheus.ui.theme.OrpheusTextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yuukifst.orpheus.R
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun AllFilesAccessDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.all_files_access_title)) },
        text = { Text(text = stringResource(id = R.string.all_files_access_description)) },
        confirmButton = {
            OrpheusTextButton(onClick = onConfirm) {
                Text(text = stringResource(id = R.string.grant_permission_button), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        dismissButton = {
            OrpheusTextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}
