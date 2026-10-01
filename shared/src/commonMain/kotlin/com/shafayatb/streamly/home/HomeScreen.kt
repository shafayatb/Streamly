package com.shafayatb.streamly.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shafayatb.streamly.core.designsystem.components.BrandBackground
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.ObserveAsEvents
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.asString
import com.shafayatb.streamly.core.presentation.feedGridColumns
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.action_retry
import streamly.shared.generated.resources.age_days
import streamly.shared.generated.resources.age_hours
import streamly.shared.generated.resources.error_network_no_internet
import streamly.shared.generated.resources.home_empty_all_body
import streamly.shared.generated.resources.home_empty_all_title
import streamly.shared.generated.resources.home_empty_live_body
import streamly.shared.generated.resources.home_empty_live_title
import streamly.shared.generated.resources.home_empty_music_body
import streamly.shared.generated.resources.home_empty_music_title
import streamly.shared.generated.resources.home_error_title
import streamly.shared.generated.resources.home_filter_all
import streamly.shared.generated.resources.home_filter_live
import streamly.shared.generated.resources.home_filter_music
import streamly.shared.generated.resources.home_loading
import streamly.shared.generated.resources.home_refresh
import streamly.shared.generated.resources.home_show_all
import streamly.shared.generated.resources.home_title
import streamly.shared.generated.resources.ic_cloud_off
import streamly.shared.generated.resources.ic_video_library
import streamly.shared.generated.resources.video_views

@Composable
fun HomeRoot(
    onNavigateToPlayer: (videoId: String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is HomeEvent.NavigateToPlayer -> onNavigateToPlayer(event.videoId)
        }
    }

    HomeScreen(state = state, onIntent = viewModel::onIntent)
}

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
) {
    val horizontalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HomeHeader(modifier = Modifier.fillMaxWidth())
        FilterChips(
            filters = state.filters,
            selected = state.selectedFilter,
            onSelect = { onIntent(HomeIntent.SelectFilter(it)) },
            modifier = Modifier.windowInsetsPadding(horizontalInsets),
        )

        val columns = feedGridColumns()
        val contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        )
        val contentModifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(horizontalInsets)

        when (val feed = state.feed) {
            FeedContent.Loading -> FeedLoading(
                columns = columns,
                contentPadding = contentPadding,
                contentDescription = stringResource(Res.string.home_loading),
                modifier = contentModifier,
            )
            is FeedContent.Loaded -> {
                // One saved scroll position per chip: switching chips starts at the top, while
                // returning from the player or rotating restores where the user was.
                val gridState = rememberSaveable(state.selectedFilter, saver = LazyGridState.Saver) {
                    LazyGridState()
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = contentModifier,
                ) {
                    items(items = feed.videos, key = { it.id }, contentType = { "video" }) { video ->
                        VideoCard(
                            video = video,
                            onClick = { onIntent(HomeIntent.OpenVideo(video.id)) },
                        )
                    }
                }
            }
            FeedContent.Empty -> {
                val (title, message) = state.selectedFilter.emptyText()
                val showAll = state.selectedFilter != FeedFilter.ALL
                FeedMessage(
                    icon = Res.drawable.ic_video_library,
                    title = stringResource(title),
                    message = stringResource(message),
                    actionLabel = stringResource(if (showAll) Res.string.home_show_all else Res.string.home_refresh),
                    onAction = {
                        onIntent(if (showAll) HomeIntent.SelectFilter(FeedFilter.ALL) else HomeIntent.Retry)
                    },
                    modifier = contentModifier,
                )
            }
            is FeedContent.Error -> FeedMessage(
                icon = Res.drawable.ic_cloud_off,
                title = stringResource(Res.string.home_error_title),
                message = feed.message.asString(),
                actionLabel = stringResource(Res.string.action_retry),
                onAction = { onIntent(HomeIntent.Retry) },
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun HomeHeader(modifier: Modifier = Modifier) {
    BrandBackground(modifier = modifier) {
        Text(
            text = stringResource(Res.string.home_title),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics { heading() },
        )
    }
}

@Composable
private fun FilterChips(
    filters: ImmutableList<FeedFilter>,
    selected: FeedFilter,
    onSelect: (FeedFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        filters.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.label())) },
                shape = CircleShape,
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

private fun FeedFilter.label(): StringResource = when (this) {
    FeedFilter.ALL -> Res.string.home_filter_all
    FeedFilter.MUSIC -> Res.string.home_filter_music
    FeedFilter.LIVE -> Res.string.home_filter_live
}

private fun FeedFilter.emptyText(): Pair<StringResource, StringResource> = when (this) {
    FeedFilter.ALL -> Res.string.home_empty_all_title to Res.string.home_empty_all_body
    FeedFilter.MUSIC -> Res.string.home_empty_music_title to Res.string.home_empty_music_body
    FeedFilter.LIVE -> Res.string.home_empty_live_title to Res.string.home_empty_live_body
}

private val PreviewVideos: ImmutableList<VideoCardUi> = persistentListOf(
    VideoCardUi(
        id = "weekly-recap",
        title = "Weekly recap: what shipped",
        channelName = "DevChannel",
        thumbnailUrl = "",
        views = UiText.PluralResource(Res.plurals.video_views, 12_400, listOf("12K")),
        age = UiText.PluralResource(Res.plurals.age_days, 3, listOf(3)),
        duration = "10:00",
    ),
    VideoCardUi(
        id = "studio-feed-live",
        title = "Streamly studio feed",
        channelName = "Streamly Live",
        thumbnailUrl = "",
        views = UiText.PluralResource(Res.plurals.video_views, 870, listOf("870")),
        age = UiText.PluralResource(Res.plurals.age_hours, 9, listOf(9)),
        duration = null,
    ),
)

@Preview
@Composable
private fun HomeScreenPreview() {
    StreamlyTheme {
        HomeScreen(state = HomeState(feed = FeedContent.Loaded(PreviewVideos)), onIntent = {})
    }
}

@Preview
@Composable
private fun HomeScreenLoadingPreview() {
    StreamlyTheme {
        HomeScreen(state = HomeState(), onIntent = {})
    }
}

@Preview
@Composable
private fun HomeScreenEmptyPreview() {
    StreamlyTheme {
        HomeScreen(state = HomeState(selectedFilter = FeedFilter.LIVE, feed = FeedContent.Empty), onIntent = {})
    }
}

@Preview
@Composable
private fun HomeScreenErrorPreview() {
    StreamlyTheme {
        HomeScreen(
            state = HomeState(feed = FeedContent.Error(UiText.Resource(Res.string.error_network_no_internet))),
            onIntent = {},
        )
    }
}
