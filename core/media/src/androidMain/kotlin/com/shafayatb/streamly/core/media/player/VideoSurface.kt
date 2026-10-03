package com.shafayatb.streamly.core.media.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.media3.ui.compose.ContentFrame

/**
 * Draws [videoId]'s frames from [player], letterboxed to the video's aspect ratio.
 *
 * It attaches only while [player] has [videoId] loaded, so a screen that is leaving (or one that
 * another screen has taken the player from) never steals the picture. Leaving the composition
 * detaches the surface; the player itself lives on. The screen stays on while the video plays.
 */
@Composable
public fun VideoSurface(
    player: ExoVideoPlayer,
    videoId: String,
    modifier: Modifier = Modifier,
) {
    val state by player.state.collectAsState()
    val isAttached = state.videoId == videoId

    ContentFrame(
        player = if (isAttached) player.exoPlayer else null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )

    val view = LocalView.current
    val keepScreenOn = isAttached && state.isPlaying
    DisposableEffect(view, keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
}
