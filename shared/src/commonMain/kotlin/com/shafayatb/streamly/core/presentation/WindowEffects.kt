package com.shafayatb.streamly.core.presentation

import androidx.compose.runtime.Composable

/**
 * Whether the host is being torn down only to be recreated for a configuration change, such as a
 * rotation. Check it when the screen stops or leaves the composition.
 */
@Composable
expect fun rememberIsChangingConfigurations(): () -> Boolean

/** Hides the system bars while in the composition, for full-screen playback. */
@Composable
expect fun ImmersiveModeEffect()
