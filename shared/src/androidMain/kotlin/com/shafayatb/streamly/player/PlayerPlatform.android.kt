package com.shafayatb.streamly.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import com.shafayatb.streamly.core.media.player.ExoVideoPlayer
import org.koin.compose.koinInject
import com.shafayatb.streamly.core.media.player.VideoSurface as MediaVideoSurface

@Composable
actual fun VideoSurface(videoId: String, modifier: Modifier) {
    // Previews have no Koin graph and no player; the black viewport stands in for the video.
    if (LocalInspectionMode.current) return
    MediaVideoSurface(player = koinInject<ExoVideoPlayer>(), videoId = videoId, modifier = modifier)
}
