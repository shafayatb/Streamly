package com.shafayatb.streamly.history

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.history_clear_body
import streamly.shared.generated.resources.history_clear_cancel
import streamly.shared.generated.resources.history_clear_confirm
import streamly.shared.generated.resources.history_clear_title

/** Clearing drops every entry and resume point for the account, so it is confirmed first. */
@Composable
fun ClearHistoryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.history_clear_title)) },
        text = { Text(stringResource(Res.string.history_clear_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.history_clear_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.history_clear_cancel)) }
        },
    )
}
