package com.shafayatb.streamly.history

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.core.presentation.UiText
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class WatchHistoryState(
    val content: WatchHistoryContent = WatchHistoryContent.Loading,
    val isClearDialogShown: Boolean = false,
) {
    val canClear: Boolean get() = content is WatchHistoryContent.Loaded
}

@Immutable
sealed interface WatchHistoryContent {
    data object Loading : WatchHistoryContent
    data object Empty : WatchHistoryContent
    data class Loaded(val items: ImmutableList<HistoryItemUi>) : WatchHistoryContent
    data class Error(val message: UiText) : WatchHistoryContent
}

@Immutable
data class HistoryItemUi(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    /** "10:34", or `null` for a live stream. */
    val durationText: String?,
    val isLive: Boolean,
    /** 0–1 for the bar on the thumbnail; `null` for live streams. */
    val progress: Float?,
    /** When it was last watched: "2 hours ago". */
    val watched: UiText,
)
