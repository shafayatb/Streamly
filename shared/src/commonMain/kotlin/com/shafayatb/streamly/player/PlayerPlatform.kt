package com.shafayatb.streamly.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Media3 is Android-only, so common UI reaches it through this declaration and never sees a
// Media3 type.

/**
 * The shared player's picture for [videoId], letterboxed to the video's aspect ratio. It stays
 * blank while the player has another video loaded.
 */
@Composable
expect fun VideoSurface(videoId: String, modifier: Modifier = Modifier)
