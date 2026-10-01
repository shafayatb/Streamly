package com.shafayatb.streamly.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shafayatb.streamly.core.designsystem.components.ChannelAvatar
import com.shafayatb.streamly.core.designsystem.components.VideoThumbnail
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.ObserveAsEvents
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.asString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.action_retry
import streamly.shared.generated.resources.cd_navigate_back
import streamly.shared.generated.resources.error_network_not_found
import streamly.shared.generated.resources.ic_arrow_back
import streamly.shared.generated.resources.player_coming_soon

@Composable
fun PlayerRoot(
    videoId: String,
    onNavigateBack: () -> Unit,
    viewModel: PlayerViewModel = koinViewModel { parametersOf(videoId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            PlayerEvent.NavigateBack -> onNavigateBack()
        }
    }

    PlayerPlaceholderScreen(state = state, onIntent = viewModel::onIntent)
}

/** Stand-in for the player: shows which video was opened until Media3 playback is wired up. */
@Composable
fun PlayerPlaceholderScreen(
    state: PlayerState,
    onIntent: (PlayerIntent) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding(),
            ) {
                when (val content = state.content) {
                    is PlayerContent.Loaded -> VideoThumbnail(
                        url = content.video.thumbnailUrl,
                        contentDescription = null,
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f)),
                        ) {
                            Text(
                                text = stringResource(Res.string.player_coming_soon),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                            )
                        }
                    }
                    PlayerContent.Loading -> Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                    is PlayerContent.Error -> Spacer(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )
                }
            }
            when (val content = state.content) {
                is PlayerContent.Loaded -> VideoDetails(video = content.video)
                is PlayerContent.Error -> PlayerError(message = content.message, onRetry = { onIntent(PlayerIntent.Retry) })
                PlayerContent.Loading -> Unit
            }
        }
        // Declared last so it draws above, and takes touches over, the scrolling content.
        IconButton(
            onClick = { onIntent(PlayerIntent.NavigateBack) },
            modifier = Modifier
                .statusBarsPadding()
                .padding(4.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_back),
                contentDescription = stringResource(Res.string.cd_navigate_back),
                tint = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun VideoDetails(video: VideoDetailsUi) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelAvatar(channelName = video.channelName)
            Spacer(Modifier.width(12.dp))
            Text(
                text = video.channelName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
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
private fun PlayerError(message: UiText, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = message.asString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text(stringResource(Res.string.action_retry))
        }
    }
}

@Preview
@Composable
private fun PlayerPlaceholderScreenPreview() {
    StreamlyTheme {
        PlayerPlaceholderScreen(
            state = PlayerState(
                content = PlayerContent.Loaded(
                    VideoDetailsUi(
                        id = "media3-in-10-minutes",
                        title = "Media3 in 10 minutes",
                        channelName = "CodeLabs",
                        description = "A quick tour of ExoPlayer, HLS playback, and offline downloads.",
                        thumbnailUrl = "",
                    ),
                ),
            ),
            onIntent = {},
        )
    }
}

@Preview
@Composable
private fun PlayerPlaceholderScreenErrorPreview() {
    StreamlyTheme {
        PlayerPlaceholderScreen(
            state = PlayerState(PlayerContent.Error(UiText.Resource(Res.string.error_network_not_found))),
            onIntent = {},
        )
    }
}
