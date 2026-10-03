package com.shafayatb.streamly.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.shafayatb.streamly.core.designsystem.components.ChannelAvatar
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.ImmersiveModeEffect
import com.shafayatb.streamly.core.presentation.ObserveAsEvents
import com.shafayatb.streamly.core.presentation.ScreenVisibilityEffect
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.asString
import com.shafayatb.streamly.core.presentation.rememberNotificationPermissionRequest
import com.shafayatb.streamly.downloads.RemoveDownloadDialog
import com.shafayatb.streamly.core.presentation.resolve
import com.shafayatb.streamly.home.VideoCard
import com.shafayatb.streamly.home.VideoCardUi
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.downloads_waiting_wifi
import streamly.shared.generated.resources.player_downloaded
import streamly.shared.generated.resources.player_download_waiting
import streamly.shared.generated.resources.player_download_removing
import streamly.shared.generated.resources.player_download_retry
import streamly.shared.generated.resources.player_download_queued
import streamly.shared.generated.resources.ic_replay
import streamly.shared.generated.resources.ic_download_done
import streamly.shared.generated.resources.cd_cancel_download
import streamly.shared.generated.resources.action_retry
import streamly.shared.generated.resources.age_days
import streamly.shared.generated.resources.error_network_no_internet
import streamly.shared.generated.resources.ic_download
import streamly.shared.generated.resources.ic_share
import streamly.shared.generated.resources.ic_thumb_up
import streamly.shared.generated.resources.player_download
import streamly.shared.generated.resources.player_like
import streamly.shared.generated.resources.player_liked
import streamly.shared.generated.resources.player_metadata
import streamly.shared.generated.resources.player_share
import streamly.shared.generated.resources.player_subscribe
import streamly.shared.generated.resources.player_subscribed
import streamly.shared.generated.resources.player_up_next
import streamly.shared.generated.resources.player_up_next_empty
import streamly.shared.generated.resources.video_views

@Composable
fun PlayerRoot(
    videoId: String,
    onNavigateBack: () -> Unit,
    onNavigateToVideo: (videoId: String) -> Unit,
    viewModel: PlayerViewModel = koinViewModel { parametersOf(videoId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            PlayerEvent.NavigateBack -> onNavigateBack()
            is PlayerEvent.NavigateToVideo -> onNavigateToVideo(event.videoId)
            is PlayerEvent.ShowMessage -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(event.message.resolve())
            }
        }
    }

    ScreenVisibilityEffect(
        onShown = { viewModel.onIntent(PlayerIntent.ScreenShown) },
        onHidden = { viewModel.onIntent(PlayerIntent.ScreenHidden) },
    )

    val requestNotificationPermission = rememberNotificationPermissionRequest()
    PlayerScreen(
        state = state,
        onIntent = { intent ->
            if (intent == PlayerIntent.Download) requestNotificationPermission()
            viewModel.onIntent(intent)
        },
        snackbarHostState = snackbarHostState,
    )
}

private enum class PlayerLayout {
    /** Phone in portrait, or a medium-width window: the player above the details and up next. */
    SINGLE_COLUMN,

    /** Expanded width: up next moves to a side column. */
    TWO_PANE,

    /** Short window, i.e. a phone in landscape: the video fills the screen. */
    FULL_SCREEN,
}

@Composable
private fun playerLayout(): PlayerLayout {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return when {
        !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ->
            PlayerLayout.FULL_SCREEN
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            PlayerLayout.TWO_PANE
        else -> PlayerLayout.SINGLE_COLUMN
    }
}

@Composable
fun PlayerScreen(
    state: PlayerState,
    onIntent: (PlayerIntent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val horizontalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (playerLayout()) {
            PlayerLayout.FULL_SCREEN -> {
                ImmersiveModeEffect()
                PlayerViewport(state = state, onIntent = onIntent, modifier = Modifier.fillMaxSize())
            }
            PlayerLayout.SINGLE_COLUMN -> Column(modifier = Modifier.fillMaxSize()) {
                InlinePlayer(state = state, onIntent = onIntent)
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp + navigationBarPadding),
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(horizontalInsets),
                ) {
                    videoDetails(state = state, onIntent = onIntent)
                    upNext(upNext = state.upNext, onIntent = onIntent)
                }
            }
            PlayerLayout.TWO_PANE -> Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(horizontalInsets),
            ) {
                Column(modifier = Modifier.weight(0.62f)) {
                    InlinePlayer(state = state, onIntent = onIntent)
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp + navigationBarPadding),
                        modifier = Modifier.weight(1f),
                    ) {
                        videoDetails(state = state, onIntent = onIntent)
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                        bottom = 16.dp + navigationBarPadding,
                    ),
                    modifier = Modifier.weight(0.38f),
                ) {
                    upNext(upNext = state.upNext, onIntent = onIntent)
                }
            }
        }
        val loaded = state.content as? PlayerContent.Loaded
        if (state.isRemoveDownloadDialogShown && loaded != null) {
            RemoveDownloadDialog(
                title = loaded.video.title,
                onConfirm = { onIntent(PlayerIntent.ConfirmRemoveDownload) },
                onDismiss = { onIntent(PlayerIntent.DismissRemoveDownload) },
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

/** The 16:9 player under the status bar, which it paints black. */
@Composable
private fun InlinePlayer(state: PlayerState, onIntent: (PlayerIntent) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .statusBarsPadding(),
    ) {
        PlayerViewport(
            state = state,
            onIntent = onIntent,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
    }
}

// One stable key whatever the state, so the list stays scrolled to the top when the details
// replace the loading state instead of keeping "Up next" in view.
private fun LazyListScope.videoDetails(state: PlayerState, onIntent: (PlayerIntent) -> Unit) {
    item(key = "details", contentType = "details") {
        when (val content = state.content) {
            is PlayerContent.Loaded -> VideoDetails(
                video = content.video,
                isLiked = state.isLiked,
                isSubscribed = state.isSubscribed,
                download = state.download,
                onIntent = onIntent,
            )
            is PlayerContent.Error -> InlineMessage(
                message = content.message.asString(),
                onRetry = { onIntent(PlayerIntent.RetryDetails) },
            )
            PlayerContent.Loading -> Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VideoDetails(
    video: VideoDetailsUi,
    isLiked: Boolean,
    isSubscribed: Boolean,
    download: DownloadActionUi,
    onIntent: (PlayerIntent) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.player_metadata, video.views.asString(), video.age.asString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelAvatar(channelName = video.channelName)
            Spacer(Modifier.width(12.dp))
            Text(
                text = video.channelName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            if (isSubscribed) {
                FilledTonalButton(onClick = { onIntent(PlayerIntent.ToggleSubscribe) }) {
                    Text(stringResource(Res.string.player_subscribed))
                }
            } else {
                Button(onClick = { onIntent(PlayerIntent.ToggleSubscribe) }) {
                    Text(stringResource(Res.string.player_subscribe))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                icon = Res.drawable.ic_thumb_up,
                label = stringResource(if (isLiked) Res.string.player_liked else Res.string.player_like),
                selected = isLiked,
                onClick = { onIntent(PlayerIntent.ToggleLike) },
            )
            ActionButton(
                icon = Res.drawable.ic_share,
                label = stringResource(Res.string.player_share),
                onClick = { onIntent(PlayerIntent.Share) },
            )
            DownloadButton(action = download, onIntent = onIntent)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = video.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowScope.DownloadButton(action: DownloadActionUi, onIntent: (PlayerIntent) -> Unit) {
    if (action == DownloadActionUi.Hidden) return
    val inProgress = action is DownloadActionUi.Downloading ||
        action == DownloadActionUi.Queued ||
        action == DownloadActionUi.WaitingForNetwork ||
        action == DownloadActionUi.WaitingForWifi
    val cancelDescription = stringResource(Res.string.cd_cancel_download)
    val waitingForWifi = stringResource(Res.string.downloads_waiting_wifi)
    FilledTonalButton(
        onClick = {
            onIntent(
                when (action) {
                    DownloadActionUi.Downloaded -> PlayerIntent.RequestRemoveDownload
                    DownloadActionUi.Idle, DownloadActionUi.Failed -> PlayerIntent.Download
                    else -> PlayerIntent.CancelDownload
                },
            )
        },
        enabled = action != DownloadActionUi.Removing,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (action == DownloadActionUi.Downloaded) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (action == DownloadActionUi.Downloaded) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        // A little wider than Like and Share, so "Downloaded" fits on a 360 dp phone.
        modifier = Modifier
            .weight(1.3f)
            .semantics {
                if (inProgress) onClick(label = cancelDescription, action = null)
                // Only "Waiting" fits on a 360 dp phone; TalkBack hears what it is waiting for.
                if (action == DownloadActionUi.WaitingForWifi) stateDescription = waitingForWifi
            },
    ) {
        when (action) {
            is DownloadActionUi.Downloading -> {
                val percent = action.percent
                if (percent == null) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    CircularProgressIndicator(
                        progress = { percent / 100f },
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            DownloadActionUi.Queued,
            DownloadActionUi.WaitingForNetwork,
            DownloadActionUi.WaitingForWifi,
            DownloadActionUi.Removing,
            ->
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else -> Icon(
                painter = painterResource(
                    when (action) {
                        DownloadActionUi.Downloaded -> Res.drawable.ic_download_done
                        DownloadActionUi.Failed -> Res.drawable.ic_replay
                        else -> Res.drawable.ic_download
                    },
                ),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = when (action) {
                is DownloadActionUi.Downloading -> action.percent?.let { "$it%" } ?: stringResource(Res.string.player_download)
                DownloadActionUi.Queued -> stringResource(Res.string.player_download_queued)
                DownloadActionUi.WaitingForNetwork, DownloadActionUi.WaitingForWifi ->
                    stringResource(Res.string.player_download_waiting)
                DownloadActionUi.Downloaded -> stringResource(Res.string.player_downloaded)
                DownloadActionUi.Failed -> stringResource(Res.string.player_download_retry)
                DownloadActionUi.Removing -> stringResource(Res.string.player_download_removing)
                else -> stringResource(Res.string.player_download)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowScope.ActionButton(
    icon: DrawableResource,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        modifier = modifier.weight(1f),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun LazyListScope.upNext(upNext: UpNextContent, onIntent: (PlayerIntent) -> Unit) {
    item(key = "up-next-title", contentType = "title") {
        Text(
            text = stringResource(Res.string.player_up_next),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                .semantics { heading() },
        )
    }
    when (upNext) {
        UpNextContent.Loading -> item(key = "up-next-loading", contentType = "message") {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                CircularProgressIndicator()
            }
        }
        is UpNextContent.Error -> item(key = "up-next-error", contentType = "message") {
            InlineMessage(message = upNext.message.asString(), onRetry = { onIntent(PlayerIntent.RetryUpNext) })
        }
        is UpNextContent.Loaded -> if (upNext.videos.isEmpty()) {
            item(key = "up-next-empty", contentType = "message") {
                Text(
                    text = stringResource(Res.string.player_up_next_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            items(items = upNext.videos, key = { "up-next-${it.id}" }, contentType = { "video" }) { video ->
                VideoCard(
                    video = video,
                    onClick = { onIntent(PlayerIntent.SelectUpNext(video.id)) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun InlineMessage(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(Res.string.action_retry))
        }
    }
}

private val PreviewVideo = VideoDetailsUi(
    id = "media3-in-10-minutes",
    title = "Media3 in 10 minutes",
    channelName = "CodeLabs",
    description = "A quick tour of ExoPlayer, HLS playback, and offline downloads.",
    thumbnailUrl = "",
    views = UiText.PluralResource(Res.plurals.video_views, 12_400, listOf("12K")),
    age = UiText.PluralResource(Res.plurals.age_days, 3, listOf(3)),
    isLive = false,
)

private val PreviewUpNext = persistentListOf(
    VideoCardUi(
        id = "weekly-recap",
        title = "Weekly recap: what shipped",
        channelName = "DevChannel",
        thumbnailUrl = "",
        views = UiText.PluralResource(Res.plurals.video_views, 870, listOf("870")),
        age = UiText.PluralResource(Res.plurals.age_days, 1, listOf(1)),
        duration = "8:12",
    ),
)

@Preview
@Composable
private fun PlayerScreenPreview() {
    StreamlyTheme {
        PlayerScreen(
            state = PlayerState(
                content = PlayerContent.Loaded(PreviewVideo),
                playback = PlaybackUi(
                    position = 83.seconds,
                    duration = 10.minutes,
                    positionText = "1:23",
                    durationText = "10:00",
                ),
                upNext = UpNextContent.Loaded(PreviewUpNext),
            ),
            onIntent = {},
        )
    }
}

@Preview
@Composable
private fun PlayerScreenErrorPreview() {
    StreamlyTheme {
        PlayerScreen(
            state = PlayerState(
                content = PlayerContent.Error(UiText.Resource(Res.string.error_network_no_internet)),
                upNext = UpNextContent.Error(UiText.Resource(Res.string.error_network_no_internet)),
            ),
            onIntent = {},
        )
    }
}
