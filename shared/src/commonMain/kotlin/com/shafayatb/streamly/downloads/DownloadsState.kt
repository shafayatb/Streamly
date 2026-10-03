package com.shafayatb.streamly.downloads

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.core.presentation.UiText
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class DownloadsState(
    val content: DownloadsContent = DownloadsContent.Loading,
    /** "66 MB used · 3.1 GB free"; `null` until known. */
    val storage: UiText? = null,
    /** The download the remove dialog is asking about, or `null` when it is closed. */
    val pendingRemoval: DownloadItemUi? = null,
)

@Immutable
sealed interface DownloadsContent {
    data object Loading : DownloadsContent
    data object Empty : DownloadsContent
    data class Loaded(val items: ImmutableList<DownloadItemUi>) : DownloadsContent
    data class Error(val message: UiText) : DownloadsContent
}

@Immutable
data class DownloadItemUi(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val status: DownloadItemStatus,
)

@Immutable
sealed interface DownloadItemStatus {
    data object Queued : DownloadItemStatus
    data object WaitingForNetwork : DownloadItemStatus
    data object WaitingForWifi : DownloadItemStatus

    /** [progress] is 0–1, or `null` while the downloader cannot tell (an indeterminate bar). */
    data class Downloading(val progress: Float?, val text: UiText) : DownloadItemStatus
    data object Failed : DownloadItemStatus

    /** [text] is the size and length, e.g. "66 MB · 10:34". */
    data class Completed(val text: UiText) : DownloadItemStatus
}
