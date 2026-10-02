package com.shafayatb.streamly.downloads

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shafayatb.streamly.core.designsystem.components.BrandBackground
import com.shafayatb.streamly.core.designsystem.components.VideoThumbnail
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.ObserveAsEvents
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.asString
import com.shafayatb.streamly.core.presentation.feedGridColumns
import com.shafayatb.streamly.home.FeedLoading
import com.shafayatb.streamly.home.FeedMessage
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.action_retry
import streamly.shared.generated.resources.cd_cancel_download
import streamly.shared.generated.resources.cd_remove_download
import streamly.shared.generated.resources.downloads_detail
import streamly.shared.generated.resources.downloads_empty_body
import streamly.shared.generated.resources.downloads_empty_title
import streamly.shared.generated.resources.downloads_error_title
import streamly.shared.generated.resources.downloads_failed
import streamly.shared.generated.resources.downloads_loading
import streamly.shared.generated.resources.downloads_progress
import streamly.shared.generated.resources.downloads_queued
import streamly.shared.generated.resources.downloads_ready
import streamly.shared.generated.resources.downloads_retry
import streamly.shared.generated.resources.downloads_storage
import streamly.shared.generated.resources.downloads_title
import streamly.shared.generated.resources.downloads_waiting
import streamly.shared.generated.resources.error_storage_unknown
import streamly.shared.generated.resources.ic_close
import streamly.shared.generated.resources.ic_cloud_off
import streamly.shared.generated.resources.ic_delete
import streamly.shared.generated.resources.ic_download

@Composable
fun DownloadsRoot(
    bottomInset: Dp,
    onNavigateToPlayer: (videoId: String) -> Unit,
    viewModel: DownloadsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is DownloadsEvent.NavigateToPlayer -> onNavigateToPlayer(event.videoId)
        }
    }

    DownloadsScreen(state = state, onIntent = viewModel::onIntent, bottomInset = bottomInset)
}

@Composable
fun DownloadsScreen(
    state: DownloadsState,
    onIntent: (DownloadsIntent) -> Unit,
    bottomInset: Dp = 0.dp,
) {
    val horizontalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val contentModifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(horizontalInsets)
    val contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp + bottomInset)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DownloadsHeader(storage = state.storage?.asString())
        when (val content = state.content) {
            DownloadsContent.Loading -> FeedLoading(
                columns = feedGridColumns(),
                contentPadding = contentPadding,
                contentDescription = stringResource(Res.string.downloads_loading),
                modifier = contentModifier,
            )
            DownloadsContent.Empty -> FeedMessage(
                icon = Res.drawable.ic_download,
                title = stringResource(Res.string.downloads_empty_title),
                message = stringResource(Res.string.downloads_empty_body),
                modifier = contentModifier,
            )
            is DownloadsContent.Error -> FeedMessage(
                icon = Res.drawable.ic_cloud_off,
                title = stringResource(Res.string.downloads_error_title),
                message = content.message.asString(),
                actionLabel = stringResource(Res.string.action_retry),
                onAction = { onIntent(DownloadsIntent.RetryLoad) },
                modifier = contentModifier,
            )
            is DownloadsContent.Loaded -> LazyVerticalGrid(
                // The feed's window-size columns: one on phones, two on medium, three on expanded.
                columns = GridCells.Fixed(feedGridColumns()),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = contentModifier,
            ) {
                items(items = content.items, key = { it.videoId }, contentType = { "download" }) { item ->
                    DownloadRow(item = item, onIntent = onIntent)
                }
            }
        }
    }

    state.pendingRemoval?.let { item ->
        RemoveDownloadDialog(
            title = item.title,
            onConfirm = { onIntent(DownloadsIntent.ConfirmRemove) },
            onDismiss = { onIntent(DownloadsIntent.DismissRemove) },
        )
    }
}

@Composable
private fun DownloadsHeader(storage: String?) {
    BrandBackground(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(Res.string.downloads_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            if (storage != null) {
                Text(
                    text = storage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItemUi, onIntent: (DownloadsIntent) -> Unit) {
    val status = item.status
    val inProgress = status is DownloadItemStatus.Downloading ||
        status == DownloadItemStatus.Queued ||
        status == DownloadItemStatus.WaitingForNetwork
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = status is DownloadItemStatus.Completed) {
                onIntent(DownloadsIntent.Open(item.videoId))
            }
            .padding(vertical = 4.dp),
    ) {
        VideoThumbnail(url = item.thumbnailUrl, contentDescription = null, modifier = Modifier.width(120.dp))
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
                text = item.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            DownloadStatusLine(status = status, onRetry = { onIntent(DownloadsIntent.Retry(item.videoId)) })
        }
        IconButton(
            onClick = {
                onIntent(
                    if (inProgress) DownloadsIntent.Cancel(item.videoId) else DownloadsIntent.RequestRemove(item.videoId),
                )
            },
        ) {
            Icon(
                painter = painterResource(if (inProgress) Res.drawable.ic_close else Res.drawable.ic_delete),
                contentDescription = stringResource(
                    if (inProgress) Res.string.cd_cancel_download else Res.string.cd_remove_download,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadStatusLine(status: DownloadItemStatus, onRetry: () -> Unit) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (status) {
        is DownloadItemStatus.Downloading -> {
            val progress = status.progress
            val barModifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
            if (progress == null) {
                LinearProgressIndicator(modifier = barModifier)
            } else {
                LinearProgressIndicator(progress = { progress }, modifier = barModifier)
            }
            Spacer(Modifier.height(4.dp))
            Text(status.text.asString(), style = labelStyle, color = muted)
        }
        DownloadItemStatus.Queued ->
            Text(stringResource(Res.string.downloads_queued), style = labelStyle, color = muted)
        DownloadItemStatus.WaitingForNetwork ->
            Text(stringResource(Res.string.downloads_waiting), style = labelStyle, color = muted)
        is DownloadItemStatus.Completed -> {
            val ready = readyColor()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ready),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.downloads_ready), style = labelStyle, color = ready)
            }
            Text(status.text.asString(), style = labelStyle, color = muted)
        }
        DownloadItemStatus.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.downloads_failed),
                style = labelStyle,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(Res.string.downloads_retry))
            }
        }
    }
}

// The mockup's "Ready to play" green, darkened on light backgrounds so the text stays legible.
@Composable
private fun readyColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF4ADE80) else Color(0xFF13804A)

private val PreviewItems = persistentListOf(
    DownloadItemUi(
        videoId = "weekly-recap",
        title = "Weekly recap: what shipped",
        channelName = "DevChannel",
        thumbnailUrl = "",
        status = DownloadItemStatus.Downloading(
            progress = 0.62f,
            text = UiText.Resource(Res.string.downloads_progress, listOf("62%", "41 MB")),
        ),
    ),
    DownloadItemUi("queued", "Late-night synthwave session", "Neon Hours", "", DownloadItemStatus.WaitingForNetwork),
    DownloadItemUi("failed", "Festival highlights", "Crowd Pulse", "", DownloadItemStatus.Failed),
    DownloadItemUi(
        videoId = "media3-in-10-minutes",
        title = "Media3 in 10 minutes",
        channelName = "CodeLabs",
        thumbnailUrl = "",
        status = DownloadItemStatus.Completed(UiText.Resource(Res.string.downloads_detail, listOf("66 MB", "10:00"))),
    ),
)

private val PreviewStorage = UiText.Resource(Res.string.downloads_storage, listOf("107 MB", "3.1 GB"))

@Preview
@Composable
private fun DownloadsScreenPreview() {
    StreamlyTheme {
        DownloadsScreen(
            state = DownloadsState(content = DownloadsContent.Loaded(PreviewItems), storage = PreviewStorage),
            onIntent = {},
        )
    }
}

@Preview
@Composable
private fun DownloadsScreenEmptyPreview() {
    StreamlyTheme {
        DownloadsScreen(state = DownloadsState(content = DownloadsContent.Empty, storage = PreviewStorage), onIntent = {})
    }
}

@Preview
@Composable
private fun DownloadsScreenErrorPreview() {
    StreamlyTheme {
        DownloadsScreen(
            state = DownloadsState(content = DownloadsContent.Error(UiText.Resource(Res.string.error_storage_unknown))),
            onIntent = {},
        )
    }
}
