package com.shafayatb.streamly.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shafayatb.streamly.core.designsystem.theme.StreamlyPalette

// Badges sit on top of thumbnails, whose colors are unknown, so they use fixed colors in both themes.

/** A video's length, e.g. "10:34", for the corner of a thumbnail. */
@Composable
public fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    VideoBadge(text = text, background = Color.Black.copy(alpha = 0.8f), modifier = modifier)
}

/** Marks a live stream, in place of a [DurationBadge]. */
@Composable
public fun LiveBadge(text: String, modifier: Modifier = Modifier) {
    VideoBadge(text = text, background = StreamlyPalette.Coral500, modifier = modifier)
}

@Composable
private fun VideoBadge(text: String, background: Color, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .background(background, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
