package com.shafayatb.streamly.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.profile_cancel
import streamly.shared.generated.resources.profile_leave_guest_body
import streamly.shared.generated.resources.profile_leave_guest_title
import streamly.shared.generated.resources.profile_sign_in
import streamly.shared.generated.resources.profile_sign_out
import streamly.shared.generated.resources.profile_sign_out_body
import streamly.shared.generated.resources.profile_sign_out_title

/**
 * Mockup 07: a rounded card with a grey Cancel pill and a red Sign out pill. A guest's version
 * leads to sign-in, so its confirm button is the primary color rather than a warning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignOutDialog(
    dialog: ProfileDialog,
    isSigningOut: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val guest = dialog == ProfileDialog.LEAVE_GUEST
    val colors = MaterialTheme.colorScheme
    BasicAlertDialog(onDismissRequest = { if (!isSigningOut) onDismiss() }) {
        Surface(shape = RoundedCornerShape(24.dp), color = colors.surface, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(if (guest) Res.string.profile_leave_guest_title else Res.string.profile_sign_out_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(if (guest) Res.string.profile_leave_guest_body else Res.string.profile_sign_out_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onDismiss,
                        enabled = !isSigningOut,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceVariant, contentColor = colors.onSurface),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(Res.string.profile_cancel)) }
                    Button(
                        onClick = onConfirm,
                        enabled = !isSigningOut,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (guest) colors.primary else colors.error,
                            contentColor = if (guest) colors.onPrimary else colors.onError,
                        ),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(if (guest) Res.string.profile_sign_in else Res.string.profile_sign_out)) }
                }
            }
        }
    }
}
