package com.shafayatb.streamly.home

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.core.presentation.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class HomeState(
    val filters: ImmutableList<FeedFilter> = FeedFilter.entries.toImmutableList(),
    val selectedFilter: FeedFilter = FeedFilter.ALL,
    val feed: FeedContent = FeedContent.Loading,
)

@Immutable
sealed interface FeedContent {
    data object Loading : FeedContent

    /** The feed loaded, but nothing matches [HomeState.selectedFilter]. */
    data object Empty : FeedContent

    data class Loaded(val videos: ImmutableList<VideoCardUi>) : FeedContent

    data class Error(val message: UiText) : FeedContent
}
