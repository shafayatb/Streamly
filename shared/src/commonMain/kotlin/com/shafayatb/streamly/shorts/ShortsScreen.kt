package com.shafayatb.streamly.shorts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.shafayatb.streamly.core.designsystem.components.ChannelAvatar
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.ObserveAsEvents
import com.shafayatb.streamly.core.presentation.ScreenVisibilityEffect
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.asString
import com.shafayatb.streamly.core.presentation.resolve
import com.shafayatb.streamly.home.FeedMessage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.action_retry
import streamly.shared.generated.resources.cd_mute
import streamly.shared.generated.resources.cd_pause
import streamly.shared.generated.resources.cd_play
import streamly.shared.generated.resources.cd_unmute
import streamly.shared.generated.resources.error_network_no_internet
import streamly.shared.generated.resources.home_refresh
import streamly.shared.generated.resources.ic_cloud_off
import streamly.shared.generated.resources.ic_comment
import streamly.shared.generated.resources.ic_play
import streamly.shared.generated.resources.ic_share
import streamly.shared.generated.resources.ic_thumb_up
import streamly.shared.generated.resources.ic_video_library
import streamly.shared.generated.resources.ic_volume_off
import streamly.shared.generated.resources.ic_volume_up
import streamly.shared.generated.resources.player_buffering
import streamly.shared.generated.resources.player_share
import streamly.shared.generated.resources.shorts_comment
import streamly.shared.generated.resources.shorts_empty_body
import streamly.shared.generated.resources.shorts_empty_title
import streamly.shared.generated.resources.shorts_error_title
import streamly.shared.generated.resources.shorts_handle
import streamly.shared.generated.resources.shorts_like
import streamly.shared.generated.resources.shorts_liked
import streamly.shared.generated.resources.shorts_loading
import streamly.shared.generated.resources.shorts_paused
import streamly.shared.generated.resources.shorts_title

/** [bottomInset] is the system bar space the app shell leaves for this screen to keep clear. */
@Composable
fun ShortsRoot(
    bottomInset: Dp,
    viewModel: ShortsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ShortsEvent.ShowMessage -> scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(event.message.resolve())
            }
        }
    }

    ScreenVisibilityEffect(
        onShown = { viewModel.onIntent(ShortsIntent.ScreenShown) },
        onHidden = { viewModel.onIntent(ShortsIntent.ScreenHidden) },
    )

    ShortsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        bottomInset = bottomInset,
        snackbarHostState = snackbarHostState,
    )
}

/** Video is always shown on black, so Shorts uses the dark scheme in either app theme. */
@Composable
fun ShortsScreen(
    state: ShortsState,
    onIntent: (ShortsIntent) -> Unit,
    bottomInset: Dp = 0.dp,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    StreamlyTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val messageModifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = bottomInset)
            when (val content = state.content) {
                ShortsContent.Loading -> {
                    val loading = stringResource(Res.string.shorts_loading)
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .semantics { contentDescription = loading },
                    )
                }
                ShortsContent.Empty -> FeedMessage(
                    icon = Res.drawable.ic_video_library,
                    title = stringResource(Res.string.shorts_empty_title),
                    message = stringResource(Res.string.shorts_empty_body),
                    actionLabel = stringResource(Res.string.home_refresh),
                    onAction = { onIntent(ShortsIntent.Retry) },
                    modifier = messageModifier,
                )
                is ShortsContent.Error -> FeedMessage(
                    icon = Res.drawable.ic_cloud_off,
                    title = stringResource(Res.string.shorts_error_title),
                    message = content.message.asString(),
                    actionLabel = stringResource(Res.string.action_retry),
                    onAction = { onIntent(ShortsIntent.Retry) },
                    modifier = messageModifier,
                )
                is ShortsContent.Loaded -> ShortsPager(
                    shorts = content.shorts,
                    state = state,
                    onIntent = onIntent,
                    bottomInset = bottomInset,
                )
            }
            ShortsHeader(
                isMuted = state.isMuted,
                showMute = state.content is ShortsContent.Loaded,
                onToggleMute = { onIntent(ShortsIntent.ToggleMute) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset),
            )
        }
    }
}

@Composable
private fun ShortsHeader(
    isMuted: Boolean,
    showMute: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = 16.dp, end = 4.dp, top = 4.dp),
    ) {
        Text(
            text = stringResource(Res.string.shorts_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (showMute) {
            IconButton(onClick = onToggleMute) {
                Icon(
                    painter = painterResource(if (isMuted) Res.drawable.ic_volume_off else Res.drawable.ic_volume_up),
                    contentDescription = stringResource(if (isMuted) Res.string.cd_unmute else Res.string.cd_mute),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ShortsPager(
    shorts: ImmutableList<ShortUi>,
    state: ShortsState,
    onIntent: (ShortsIntent) -> Unit,
    bottomInset: Dp,
) {
    val pagerState = rememberPagerState(initialPage = state.currentPage) { shorts.size }
    val currentOnIntent by rememberUpdatedState(onIntent)
    // Only a settled page drives the players, so a fling across several pages loads none of the
    // pages it passes.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnIntent(ShortsIntent.PageSettled(it)) }
    }
    val letterbox = isLetterboxed()

    VerticalPager(
        state = pagerState,
        key = { shorts[it].id },
        // Composes the neighbours ahead of a swipe, so the prepared short's first frame is
        // already on its surface when it slides in.
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        ShortPage(
            short = shorts[page],
            playback = state.playback.takeIf { page == state.currentPage },
            letterbox = letterbox,
            bottomInset = bottomInset,
            onIntent = onIntent,
        )
    }
}

/** Windows wider than a phone show the 9:16 short centered, with black bars, rather than stretched. */
@Composable
private fun isLetterboxed(): Boolean = currentWindowAdaptiveInfo().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

/** [playback] is `null` for pages other than the current one, which show no indicators. */
@Composable
private fun ShortPage(
    short: ShortUi,
    playback: ShortPlaybackUi?,
    letterbox: Boolean,
    bottomInset: Dp,
    onIntent: (ShortsIntent) -> Unit,
) {
    val toggleLabel = stringResource(if (playback?.isPaused == true) Res.string.cd_play else Res.string.cd_pause)
    val actionsPadding = Modifier.padding(bottom = bottomInset + 16.dp)

    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        Box(
            modifier = (if (letterbox) Modifier.fillMaxHeight().aspectRatio(9f / 16f) else Modifier.fillMaxSize())
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClickLabel = toggleLabel,
                    onClick = { onIntent(ShortsIntent.TogglePlayPause) },
                ),
        ) {
            ShortSurface(shortId = short.id, modifier = Modifier.fillMaxSize())
            Scrims()
            if (playback != null) {
                PlaybackIndicators(playback = playback, onIntent = onIntent)
            }
            ShortDetails(
                short = short,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = if (letterbox) 16.dp else 88.dp)
                    .then(actionsPadding),
            )
            if (!letterbox) {
                ShortActions(
                    short = short,
                    onIntent = onIntent,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp)
                        .then(actionsPadding),
                )
            }
        }
        if (letterbox) {
            ShortActions(
                short = short,
                onIntent = onIntent,
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .padding(start = 12.dp)
                    .then(actionsPadding),
            )
        }
    }
}

/** Darkens the top and bottom of the video so the white header, details, and actions stay legible. */
@Composable
private fun BoxScope.Scrims() {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(120.dp)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(240.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
    )
}

@Composable
private fun BoxScope.PlaybackIndicators(playback: ShortPlaybackUi, onIntent: (ShortsIntent) -> Unit) {
    val error = playback.error
    when {
        error != null -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 320.dp)
                .padding(24.dp),
        ) {
            Text(
                text = error.asString(),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { onIntent(ShortsIntent.RetryPlayback) }) {
                Text(stringResource(Res.string.action_retry))
            }
        }
        playback.isBuffering -> {
            val buffering = stringResource(Res.string.player_buffering)
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .semantics { contentDescription = buffering },
            )
        }
    }
    AnimatedVisibility(
        visible = error == null && playback.isPaused,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 1.2f),
        modifier = Modifier.align(Alignment.Center),
    ) {
        val paused = stringResource(Res.string.shorts_paused)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .semantics { contentDescription = paused },
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun ShortDetails(short: ShortUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelAvatar(channelName = short.channelName, size = 32.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.shorts_handle, short.channelHandle),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = short.title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShortActions(short: ShortUi, onIntent: (ShortsIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        ShortAction(label = short.likes) {
            FilledIconToggleButton(
                checked = short.isLiked,
                onCheckedChange = { onIntent(ShortsIntent.ToggleLike(short.id)) },
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = ActionContainer,
                    contentColor = Color.White,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.size(ActionSize),
            ) {
                ActionIcon(
                    icon = Res.drawable.ic_thumb_up,
                    contentDescription = stringResource(if (short.isLiked) Res.string.shorts_liked else Res.string.shorts_like),
                )
            }
        }
        ShortAction(label = short.comments) {
            ActionButton(
                icon = Res.drawable.ic_comment,
                contentDescription = stringResource(Res.string.shorts_comment),
                onClick = { onIntent(ShortsIntent.Comment(short.id)) },
            )
        }
        ShortAction(label = stringResource(Res.string.player_share)) {
            ActionButton(
                icon = Res.drawable.ic_share,
                contentDescription = stringResource(Res.string.player_share),
                onClick = { onIntent(ShortsIntent.Share(short.id)) },
            )
        }
    }
}

@Composable
private fun ShortAction(label: String, button: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        button()
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun ActionButton(icon: DrawableResource, contentDescription: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = ActionContainer, contentColor = Color.White),
        modifier = Modifier.size(ActionSize),
    ) {
        ActionIcon(icon = icon, contentDescription = contentDescription)
    }
}

@Composable
private fun ActionIcon(icon: DrawableResource, contentDescription: String) {
    Icon(painter = painterResource(icon), contentDescription = contentDescription, modifier = Modifier.size(24.dp))
}

private val ActionSize = 48.dp
private val ActionContainer = Color.White.copy(alpha = 0.18f)

private val PreviewShorts = persistentListOf(
    ShortUi(
        id = "meadow-bunny",
        title = "Meet the meadow bunny",
        channelName = "Toon Garden",
        channelHandle = "toongarden",
        likes = "231K",
        comments = "5.8K",
        isLiked = true,
    ),
)

@Preview
@Composable
private fun ShortsScreenPreview() {
    ShortsScreen(
        state = ShortsState(
            content = ShortsContent.Loaded(PreviewShorts),
            playback = ShortPlaybackUi(isPaused = true),
        ),
        onIntent = {},
    )
}

@Preview
@Composable
private fun ShortsScreenErrorPreview() {
    ShortsScreen(
        state = ShortsState(content = ShortsContent.Error(UiText.Resource(Res.string.error_network_no_internet))),
        onIntent = {},
    )
}
