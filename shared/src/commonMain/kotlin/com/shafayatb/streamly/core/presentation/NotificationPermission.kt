package com.shafayatb.streamly.core.presentation

import androidx.compose.runtime.Composable

/**
 * Returns an action that asks for permission to show notifications where the platform requires
 * it and has not granted it yet. Nothing waits for the answer.
 */
@Composable
expect fun rememberNotificationPermissionRequest(): () -> Unit
