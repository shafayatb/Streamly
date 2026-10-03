package com.shafayatb.streamly.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.shafayatb.streamly.core.designsystem.theme.StreamlyPalette

private val BrandGradient: Brush = Brush.linearGradient(
    colors = listOf(StreamlyPalette.Indigo900, StreamlyPalette.Indigo500),
)

/** The deep indigo gradient used behind onboarding and other brand moments. */
@Composable
public fun BrandBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier.background(BrandGradient),
        content = content,
    )
}
