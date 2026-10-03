package com.shafayatb.streamly.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shafayatb.streamly.core.designsystem.components.LiveBadge
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.asString
import com.shafayatb.streamly.core.presentation.formatDuration
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.action_retry
import streamly.shared.generated.resources.cd_enter_fullscreen
import streamly.shared.generated.resources.cd_exit_fullscreen
import streamly.shared.generated.resources.cd_mute
import streamly.shared.generated.resources.cd_navigate_back
import streamly.shared.generated.resources.cd_pause
import streamly.shared.generated.resources.cd_play
import streamly.shared.generated.resources.cd_replay
import streamly.shared.generated.resources.cd_seek
import streamly.shared.generated.resources.cd_unmute
import streamly.shared.generated.resources.ic_arrow_back
import streamly.shared.generated.resources.ic_fullscreen
import streamly.shared.generated.resources.ic_fullscreen_exit
import streamly.shared.generated.resources.ic_pause
import streamly.shared.generated.resources.ic_play
import streamly.shared.generated.resources.ic_replay
import streamly.shared.generated.resources.ic_volume_off
import streamly.shared.generated.resources.ic_volume_up
import streamly.shared.generated.resources.player_buffering
import streamly.shared.generated.resources.player_loading
import streamly.shared.generated.resources.video_live

private val ControlsAutoHideDelay = 3.seconds

/**
 * The video with its controls drawn over it. A tap shows or hides the controls; while the video
 * plays they hide on their own, and they stay up while it is paused, ended, failed, or scrubbed.
 * The buffering spinner shows whether or not the controls do.
 */
@Composable
internal fun PlayerViewport(
    state: PlayerState,
    onIntent: (PlayerIntent) -> Unit,
    isFullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val playback = state.playback
    val video = (state.content as? PlayerContent.Loaded)?.video

    var controlsShown by rememberSaveable { mutableStateOf(true) }
    // Bumped on every interaction so the auto-hide countdown starts over.
    var interactions by remember { mutableIntStateOf(0) }
    var scrubPosition by remember { mutableStateOf<Duration?>(null) }

    val keepControls = !playback.isPlaying || playback.error != null || scrubPosition != null
    LaunchedEffect(controlsShown, keepControls, interactions) {
        if (controlsShown && !keepControls) {
            delay(ControlsAutoHideDelay)
            controlsShown = false
        }
    }

    val showSpinner = state.content == PlayerContent.Loading ||
        (playback.isBuffering && playback.error == null)

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(interactionSource = null, indication = null) {
                controlsShown = !controlsShown
                interactions++
            },
    ) {
        if (video != null) {
            VideoSurface(videoId = video.id, modifier = Modifier.fillMaxSize())
        }
        if (showSpinner) {
            val label = stringResource(
                if (state.content == PlayerContent.Loading) Res.string.player_loading else Res.string.player_buffering,
            )
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .semantics { contentDescription = label },
            )
        }
        val error = playback.error
        if (video != null && error == null) {
            AnimatedVisibility(
                visible = controlsShown || keepControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                ControlsOverlay(
                    playback = playback,
                    isFullscreen = isFullscreen,
                    showPlayPause = !showSpinner,
                    scrubPosition = scrubPosition,
                    onScrub = {
                        scrubPosition = it
                        interactions++
                    },
                    onScrubFinished = {
                        scrubPosition?.let { onIntent(PlayerIntent.SeekTo(it)) }
                        scrubPosition = null
                    },
                    onIntent = { intent ->
                        onIntent(intent)
                        interactions++
                    },
                )
            }
        }
        if (error != null) {
            PlaybackErrorOverlay(message = error, onRetry = { onIntent(PlayerIntent.RetryPlayback) })
        }
        // Declared last so it draws above, and takes touches over, everything else. It stays
        // reachable while the details are loading or failed, when there are no other controls.
        AnimatedVisibility(
            visible = controlsShown || keepControls || video == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            OverlayIconButton(
                icon = Res.drawable.ic_arrow_back,
                contentDescription = Res.string.cd_navigate_back,
                onClick = { onIntent(PlayerIntent.NavigateBack) },
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
private fun ControlsOverlay(
    playback: PlaybackUi,
    isFullscreen: Boolean,
    showPlayPause: Boolean,
    scrubPosition: Duration?,
    onScrub: (Duration) -> Unit,
    onScrubFinished: () -> Unit,
    onIntent: (PlayerIntent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        if (showPlayPause) {
            val (icon, label) = when {
                playback.isEnded -> Res.drawable.ic_replay to Res.string.cd_replay
                playback.isPlaying -> Res.drawable.ic_pause to Res.string.cd_pause
                else -> Res.drawable.ic_play to Res.string.cd_play
            }
            IconButton(
                onClick = { onIntent(PlayerIntent.TogglePlayPause) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = stringResource(label),
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playback.isLive) {
                    LiveBadge(text = stringResource(Res.string.video_live))
                } else {
                    val position = scrubPosition?.let(::formatDuration) ?: playback.positionText
                    Text(
                        text = playback.durationText?.let { "$position / $it" } ?: position,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.weight(1f))
                OverlayIconButton(
                    icon = if (playback.isMuted) Res.drawable.ic_volume_off else Res.drawable.ic_volume_up,
                    contentDescription = if (playback.isMuted) Res.string.cd_unmute else Res.string.cd_mute,
                    onClick = { onIntent(PlayerIntent.ToggleMute) },
                )
                OverlayIconButton(
                    icon = if (isFullscreen) Res.drawable.ic_fullscreen_exit else Res.drawable.ic_fullscreen,
                    contentDescription = if (isFullscreen) Res.string.cd_exit_fullscreen else Res.string.cd_enter_fullscreen,
                    onClick = { onIntent(if (isFullscreen) PlayerIntent.ExitFullscreen else PlayerIntent.EnterFullscreen) },
                )
            }
            if (!playback.isLive) {
                SeekBar(
                    position = scrubPosition ?: playback.position,
                    duration = playback.duration,
                    onScrub = onScrub,
                    onScrubFinished = onScrubFinished,
                )
            } else {
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun SeekBar(
    position: Duration,
    duration: Duration?,
    onScrub: (Duration) -> Unit,
    onScrubFinished: () -> Unit,
) {
    val durationMs = duration?.inWholeMilliseconds?.takeIf { it > 0 }
    val enabled = durationMs != null
    val label = stringResource(Res.string.cd_seek)
    val colors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
        disabledThumbColor = Color.White.copy(alpha = 0.5f),
        disabledInactiveTrackColor = Color.White.copy(alpha = 0.2f),
    )
    // A thin track and a small round thumb keep the bar out of the way of the video.
    Slider(
        value = durationMs?.let { position.inWholeMilliseconds.coerceIn(0, it).toFloat() } ?: 0f,
        onValueChange = { onScrub(it.toLong().milliseconds) },
        onValueChangeFinished = onScrubFinished,
        valueRange = 0f..(durationMs ?: 1L).toFloat(),
        enabled = enabled,
        colors = colors,
        thumb = {
            Box(
                Modifier
                    .size(14.dp)
                    .background(if (enabled) Color.White else Color.White.copy(alpha = 0.5f), CircleShape),
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                enabled = enabled,
                colors = colors,
                drawStopIndicator = null,
                thumbTrackGapSize = 0.dp,
                modifier = Modifier.height(4.dp),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics { contentDescription = label },
    )
}

@Composable
private fun PlaybackErrorOverlay(message: UiText, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(24.dp),
    ) {
        Text(
            text = message.asString(),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        ) {
            Text(stringResource(Res.string.action_retry))
        }
    }
}

@Composable
private fun OverlayIconButton(
    icon: DrawableResource,
    contentDescription: StringResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(contentDescription),
            tint = Color.White,
        )
    }
}
