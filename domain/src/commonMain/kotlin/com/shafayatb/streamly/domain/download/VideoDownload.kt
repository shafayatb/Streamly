package com.shafayatb.streamly.domain.download

import kotlin.time.Duration

/** A video saved, or being saved, for offline playback. */
public data class VideoDownload(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    /** [Duration.ZERO] when the download's stored details do not say. */
    val duration: Duration,
    val status: DownloadStatus,
    /** 0–100 as reported by the downloader, or `null` while it cannot tell yet. */
    val percent: Float?,
    val bytesDownloaded: Long,
) {
    /** Still on its way: it can be cancelled, and it cannot be played offline yet. */
    val isInProgress: Boolean
        get() = status == DownloadStatus.QUEUED ||
            status == DownloadStatus.WAITING_FOR_NETWORK ||
            status == DownloadStatus.DOWNLOADING
}

public enum class DownloadStatus {
    /** Waiting for a free download slot. */
    QUEUED,

    /** Waiting for a network connection; it continues on its own when one returns. */
    WAITING_FOR_NETWORK,
    DOWNLOADING,

    /** Fully saved; plays offline. */
    COMPLETED,

    /** Gave up after retrying; [DownloadRepository.retry] starts it again. */
    FAILED,

    /** Being deleted from the device. */
    REMOVING,
}

/** Space used by downloads and space left on the device, in bytes. */
public data class StorageUsage(
    val usedBytes: Long,
    val freeBytes: Long,
)
