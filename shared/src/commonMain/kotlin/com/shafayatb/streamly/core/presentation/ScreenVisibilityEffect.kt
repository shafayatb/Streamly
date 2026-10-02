package com.shafayatb.streamly.core.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Reports when a playback screen is really shown or hidden: the app moving to or from the
 * background, the entry being popped or replaced (Nav3 drops a removed entry's lifecycle to
 * CREATED at once, before its exit animation), or the screen leaving the composition. A
 * configuration change recreates the activity but is neither, so rotation never pauses playback.
 */
@Composable
fun ScreenVisibilityEffect(onShown: () -> Unit, onHidden: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val isChangingConfigurations = rememberIsChangingConfigurations()
    val currentOnShown by rememberUpdatedState(onShown)
    val currentOnHidden by rememberUpdatedState(onHidden)

    DisposableEffect(lifecycleOwner, isChangingConfigurations) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> currentOnShown()
                Lifecycle.Event.ON_STOP -> if (!isChangingConfigurations()) currentOnHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!isChangingConfigurations()) currentOnHidden()
        }
    }
}
