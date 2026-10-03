package com.shafayatb.streamly.core.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Whether the host is being torn down only to be recreated for a configuration change, such as a
 * rotation. Check it when the screen stops or leaves the composition.
 */
@Composable
expect fun rememberIsChangingConfigurations(): () -> Boolean

/** Whether the app shares the screen (split-screen or freeform), where orientation requests are ignored. */
@Composable
expect fun isInMultiWindowMode(): Boolean

/** Hides the system bars while in the composition, for full-screen playback. */
@Composable
expect fun ImmersiveModeEffect()

/**
 * Matches the window to the app's theme rather than the system's: the background drawn behind
 * Compose (e.g. during transitions) and the navigation bar's icons.
 */
@Composable
expect fun SystemBarsEffect(darkTheme: Boolean, windowBackground: Color)
