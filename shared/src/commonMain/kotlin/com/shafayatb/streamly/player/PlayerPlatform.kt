package com.shafayatb.streamly.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Media3 and the window are Android-only, so common UI reaches them through these declarations
// and never sees a Media3 type.

/**
 * The shared player's picture for [videoId], letterboxed to the video's aspect ratio. It stays
 * blank while the player has another video loaded.
 */
@Composable
expect fun VideoSurface(videoId: String, modifier: Modifier = Modifier)

/**
 * Whether the host is being torn down only to be recreated for a configuration change, such as a
 * rotation. Check it when the screen stops or leaves the composition.
 */
@Composable
expect fun rememberIsChangingConfigurations(): () -> Boolean

/** Hides the system bars while in the composition, for full-screen playback. */
@Composable
expect fun ImmersiveModeEffect()
