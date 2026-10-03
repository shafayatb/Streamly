package com.shafayatb.streamly.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shafayatb.streamly.core.designsystem.components.DurationBadge
import com.shafayatb.streamly.core.designsystem.components.LiveBadge
import com.shafayatb.streamly.core.designsystem.components.VideoThumbnail
import com.shafayatb.streamly.core.designsystem.components.WatchProgressBar
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.BrandTopBar
import com.shafayatb.streamly.core.presentation.ObserveAsEvents
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.asString
import com.shafayatb.streamly.core.presentation.feedGridColumns
import com.shafayatb.streamly.core.presentation.resolve
import com.shafayatb.streamly.home.FeedLoading
import com.shafayatb.streamly.home.FeedMessage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.action_retry
import streamly.shared.generated.resources.cd_remove_from_history
import streamly.shared.generated.resources.history_clear_all
import streamly.shared.generated.resources.history_empty_body
import streamly.shared.generated.resources.history_empty_title
import streamly.shared.generated.resources.history_error_title
import streamly.shared.generated.resources.history_loading
import streamly.shared.generated.resources.history_title
import streamly.shared.generated.resources.ic_close
import streamly.shared.generated.resources.ic_cloud_off
import streamly.shared.generated.resources.ic_history
import streamly.shared.generated.resources.player_metadata
import streamly.shared.generated.resources.video_live

@Composable
fun WatchHistoryRoot(
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (videoId: String) -> Unit,
    viewModel: WatchHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            WatchHistoryEvent.NavigateBack -> onNavigateBack()
            is WatchHistoryEvent.NavigateToPlayer -> onNavigateToPlayer(event.videoId)
            is WatchHistoryEvent.ShowMessage -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(event.message.resolve())
            }
        }
    }

    WatchHistoryScreen(state = state, onIntent = viewModel::onIntent, snackbarHostState = snackbarHostState)
}

/** A pushed destination with no tab bar, so it keeps the system navigation bar clear itself. */
@Composable
fun WatchHistoryScreen(
    state: WatchHistoryState,
    onIntent: (WatchHistoryIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val contentModifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp + navigationBar)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BrandTopBar(
                title = stringResource(Res.string.history_title),
                onBack = { onIntent(WatchHistoryIntent.NavigateBack) },
            ) {
                if (state.canClear) {
                    TextButton(onClick = { onIntent(WatchHistoryIntent.RequestClear) }) {
                        Text(stringResource(Res.string.history_clear_all), color = Color.White)
                    }
                }
            }
            when (val content = state.content) {
                WatchHistoryContent.Loading -> FeedLoading(
                    columns = feedGridColumns(),
                    contentPadding = contentPadding,
                    contentDescription = stringResource(Res.string.history_loading),
                    modifier = contentModifier,
                )
                WatchHistoryContent.Empty -> FeedMessage(
                    icon = Res.drawable.ic_history,
                    title = stringResource(Res.string.history_empty_title),
                    message = stringResource(Res.string.history_empty_body),
                    modifier = contentModifier,
                )
                is WatchHistoryContent.Error -> FeedMessage(
                    icon = Res.drawable.ic_cloud_off,
                    title = stringResource(Res.string.history_error_title),
                    message = content.message.asString(),
                    actionLabel = stringResource(Res.string.action_retry),
                    onAction = { onIntent(WatchHistoryIntent.RetryLoad) },
                    modifier = contentModifier,
                )
                is WatchHistoryContent.Loaded -> LazyVerticalGrid(
                    // The feed's window-size columns: one on phones, two on medium, three on expanded.
                    columns = GridCells.Fixed(feedGridColumns()),
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = contentModifier,
                ) {
                    items(items = content.items, key = { it.videoId }, contentType = { "history" }) { item ->
                        HistoryRow(item = item, onIntent = onIntent)
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    if (state.isClearDialogShown) {
        ClearHistoryDialog(
            onConfirm = { onIntent(WatchHistoryIntent.ConfirmClear) },
            onDismiss = { onIntent(WatchHistoryIntent.DismissClear) },
        )
    }
}

@Composable
private fun HistoryRow(item: HistoryItemUi, onIntent: (WatchHistoryIntent) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onIntent(WatchHistoryIntent.Open(item.videoId)) }
            .padding(vertical = 4.dp),
    ) {
        VideoThumbnail(url = item.thumbnailUrl, contentDescription = null, modifier = Modifier.width(120.dp)) {
            // The badge sits above the progress bar so the bar never hides it.
            val badgeModifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = if (item.progress != null) 10.dp else 6.dp)
            if (item.isLive) {
                LiveBadge(text = stringResource(Res.string.video_live), modifier = badgeModifier)
            } else {
                item.durationText?.let { DurationBadge(text = it, modifier = badgeModifier) }
            }
            item.progress?.let { WatchProgressBar(progress = it, modifier = Modifier.align(Alignment.BottomStart)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.player_metadata, item.channelName, item.watched.asString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onIntent(WatchHistoryIntent.Remove(item.videoId)) }) {
            Icon(
                painter = painterResource(Res.drawable.ic_close),
                contentDescription = stringResource(Res.string.cd_remove_from_history),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val PreviewItems = persistentListOf(
    HistoryItemUi("media3", "Media3 in 10 minutes", "CodeLabs", "", "10:00", false, 0.3f, UiText.DynamicString("2 hours ago")),
    HistoryItemUi("live", "Late-night synthwave session", "Neon Hours", "", null, true, null, UiText.DynamicString("Yesterday")),
    HistoryItemUi("done", "Festival highlights", "Crowd Pulse", "", "4:12", false, 1f, UiText.DynamicString("3 days ago")),
)

@Preview
@Composable
private fun WatchHistoryScreenPreview() {
    StreamlyTheme {
        WatchHistoryScreen(state = WatchHistoryState(content = WatchHistoryContent.Loaded(PreviewItems)), onIntent = {})
    }
}

@Preview
@Composable
private fun WatchHistoryEmptyPreview() {
    StreamlyTheme {
        WatchHistoryScreen(state = WatchHistoryState(content = WatchHistoryContent.Empty), onIntent = {})
    }
}
