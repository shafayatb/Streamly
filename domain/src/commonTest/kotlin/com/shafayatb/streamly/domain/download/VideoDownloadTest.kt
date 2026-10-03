package com.shafayatb.streamly.domain.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class VideoDownloadTest {

    private fun download(status: DownloadStatus) = VideoDownload(
        videoId = "v1",
        title = "Title",
        channelName = "Channel",
        thumbnailUrl = "",
        duration = 10.minutes,
        status = status,
        percent = null,
        bytesDownloaded = 0,
    )

    @Test
    fun onlyUnfinishedDownloadsAreInProgress() {
        val inProgress = DownloadStatus.entries.filter { download(it).isInProgress }
        assertEquals(
            listOf(
                DownloadStatus.QUEUED,
                DownloadStatus.WAITING_FOR_NETWORK,
                DownloadStatus.WAITING_FOR_WIFI,
                DownloadStatus.DOWNLOADING,
            ),
            inProgress,
        )
    }
}
