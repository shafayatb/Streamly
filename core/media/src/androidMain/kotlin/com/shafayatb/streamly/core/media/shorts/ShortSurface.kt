package com.shafayatb.streamly.core.media.shorts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.media3.ui.compose.ContentFrame

/**
 * Draws [shortId]'s frames from whichever pool player holds it, scaled with [contentScale].
 *
 * Like the long-form surface, it attaches only while a player holds [shortId], so a page whose
 * player was recycled for another short never shows that short's picture. A prepared neighbour
 * shows its first frame, so a swipe reveals a picture rather than a black page. The screen stays
 * on while the visible short plays.
 */
@Composable
public fun ShortSurface(
    pool: ExoShortsPlayerPool,
    shortId: String,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val state by pool.state.collectAsState()
    val player = if (shortId in state.players) pool.playerFor(shortId) else null

    ContentFrame(player = player, contentScale = contentScale, modifier = modifier)

    val view = LocalView.current
    val keepScreenOn = state.visibleId == shortId && state.visible?.isPlaying == true
    DisposableEffect(view, keepScreenOn) {
        if (keepScreenOn) view.keepScreenOn = true
        onDispose { if (keepScreenOn) view.keepScreenOn = false }
    }
}
