package com.shafayatb.streamly.player

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.VideoDownload

/** The Player's Download action. Every percent comes from the downloader. */
@Immutable
sealed interface DownloadActionUi {
    /** Live streams cannot be downloaded, and nothing shows before the video's details load. */
    data object Hidden : DownloadActionUi
    data object Idle : DownloadActionUi
    data object Queued : DownloadActionUi
    data object WaitingForNetwork : DownloadActionUi

    /** [percent] is `null` while the downloader cannot tell yet. */
    data class Downloading(val percent: Int?) : DownloadActionUi
    data object Downloaded : DownloadActionUi
    data object Failed : DownloadActionUi
    data object Removing : DownloadActionUi
}

/**
 * [isLive] is `null` until the video's details load. [isStarting] covers the gap between a tap
 * and the downloader reporting the new download.
 */
internal fun downloadActionOf(isLive: Boolean?, download: VideoDownload?, isStarting: Boolean): DownloadActionUi =
    when {
        isLive == null || isLive -> DownloadActionUi.Hidden
        download == null -> if (isStarting) DownloadActionUi.Queued else DownloadActionUi.Idle
        else -> when (download.status) {
            DownloadStatus.QUEUED -> DownloadActionUi.Queued
            DownloadStatus.WAITING_FOR_NETWORK -> DownloadActionUi.WaitingForNetwork
            DownloadStatus.DOWNLOADING -> DownloadActionUi.Downloading(download.percent?.toInt())
            DownloadStatus.COMPLETED -> DownloadActionUi.Downloaded
            DownloadStatus.FAILED -> DownloadActionUi.Failed
            DownloadStatus.REMOVING -> DownloadActionUi.Removing
        }
    }
