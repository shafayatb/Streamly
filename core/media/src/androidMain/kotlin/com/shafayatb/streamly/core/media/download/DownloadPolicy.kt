package com.shafayatb.streamly.core.media.download

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.scheduler.Requirements
import com.shafayatb.streamly.domain.settings.DownloadQuality

/** Wi-Fi only means an unmetered network; Media3 then also requires a network. */
@OptIn(UnstableApi::class)
internal fun requirementsFor(wifiOnly: Boolean): Requirements =
    Requirements(if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK)

internal data class VideoSizeCap(val width: Int, val height: Int)

/** 16:9 caps: the catalog's long-form videos are landscape. */
internal fun DownloadQuality.maxVideoSize(): VideoSizeCap = when (this) {
    DownloadQuality.DATA_SAVER -> VideoSizeCap(640, 360)
    DownloadQuality.STANDARD -> VideoSizeCap(854, 480)
    DownloadQuality.HIGH -> VideoSizeCap(1280, 720)
}
