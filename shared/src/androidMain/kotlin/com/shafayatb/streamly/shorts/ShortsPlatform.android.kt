package com.shafayatb.streamly.shorts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import com.shafayatb.streamly.core.media.shorts.ExoShortsPlayerPool
import org.koin.compose.koinInject
import com.shafayatb.streamly.core.media.shorts.ShortSurface as MediaShortSurface

@Composable
actual fun ShortSurface(shortId: String, modifier: Modifier) {
    // Previews have no Koin graph and no players; the black page stands in for the video.
    if (LocalInspectionMode.current) return
    MediaShortSurface(
        pool = koinInject<ExoShortsPlayerPool>(),
        shortId = shortId,
        // Fills the page on phones. Wider windows size the page to 9:16 first, so cropping a
        // vertical short there changes nothing and a landscape one fills it rather than shrinking.
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
