package com.shafayatb.streamly.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shafayatb.streamly.core.designsystem.theme.StreamlyPalette

/**
 * How much of a video has been watched ([progress], 0–1), drawn along the bottom edge of a
 * [VideoThumbnail]. Fixed colors, like the badges, because the image under it is unknown.
 */
@Composable
public fun WatchProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.White.copy(alpha = 0.4f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(StreamlyPalette.Coral500),
        )
    }
}
