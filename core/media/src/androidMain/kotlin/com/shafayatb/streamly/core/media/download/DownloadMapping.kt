package com.shafayatb.streamly.core.media.download

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.VideoDownload
import kotlin.time.Duration.Companion.milliseconds

/**
 * A [Download]'s values at one moment. A Media3 `Download` reads its progress live, so the same
 * instance changes under a `StateFlow`; copying the values lets equal snapshots be skipped and
 * changed ones be emitted.
 */
internal data class DownloadSnapshot(
    val videoId: String,
    val state: Int,
    val percentDownloaded: Float,
    val bytesDownloaded: Long,
    val startTimeMs: Long,
    val metadata: DownloadMetadata,
)

@OptIn(UnstableApi::class)
internal fun Download.toSnapshot(): DownloadSnapshot = DownloadSnapshot(
    videoId = request.id,
    state = state,
    percentDownloaded = percentDownloaded,
    bytesDownloaded = bytesDownloaded,
    startTimeMs = startTimeMs,
    metadata = DownloadMetadata.decode(request.data, videoId = request.id),
)

/** The app's only download requirement is a network, so any unmet requirement means offline. */
@OptIn(UnstableApi::class)
internal fun downloadStatusOf(state: Int, notMetRequirements: Int): DownloadStatus = when (state) {
    Download.STATE_QUEUED ->
        if (notMetRequirements != 0) DownloadStatus.WAITING_FOR_NETWORK else DownloadStatus.QUEUED
    Download.STATE_DOWNLOADING, Download.STATE_RESTARTING -> DownloadStatus.DOWNLOADING
    Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
    Download.STATE_FAILED -> DownloadStatus.FAILED
    Download.STATE_REMOVING -> DownloadStatus.REMOVING
    // STATE_STOPPED needs a stop reason, which the app never sets.
    else -> DownloadStatus.QUEUED
}

internal fun DownloadSnapshot.toVideoDownload(notMetRequirements: Int): VideoDownload {
    val status = downloadStatusOf(state, notMetRequirements)
    return VideoDownload(
        videoId = videoId,
        title = metadata.title,
        channelName = metadata.channelName,
        thumbnailUrl = metadata.thumbnailUrl,
        duration = metadata.durationMs.milliseconds,
        status = status,
        percent = when {
            status == DownloadStatus.COMPLETED -> 100f
            // Media3 reports C.PERCENTAGE_UNSET (-1) until it knows the total size.
            percentDownloaded < 0f -> null
            else -> percentDownloaded.coerceAtMost(100f)
        },
        bytesDownloaded = bytesDownloaded,
    )
}

internal fun Collection<DownloadSnapshot>.toVideoDownloads(notMetRequirements: Int): List<VideoDownload> =
    sortedByDescending { it.startTimeMs }.map { it.toVideoDownload(notMetRequirements) }
