package com.shafayatb.streamly.downloads

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.download_remove_body
import streamly.shared.generated.resources.download_remove_cancel
import streamly.shared.generated.resources.download_remove_confirm
import streamly.shared.generated.resources.download_remove_title

/** Removing a finished download deletes it for good, so it is confirmed first. */
@Composable
fun RemoveDownloadDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.download_remove_title)) },
        text = { Text(stringResource(Res.string.download_remove_body, title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.download_remove_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.download_remove_cancel)) }
        },
    )
}
