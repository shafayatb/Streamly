package com.shafayatb.streamly.shorts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [shortId]'s picture from the Shorts player pool, cropped to fill its bounds. It stays blank
 * while no pool player holds that short.
 */
@Composable
expect fun ShortSurface(shortId: String, modifier: Modifier = Modifier)
