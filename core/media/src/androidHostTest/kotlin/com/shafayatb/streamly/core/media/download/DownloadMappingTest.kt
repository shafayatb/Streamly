package com.shafayatb.streamly.core.media.download

import androidx.media3.common.C
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.scheduler.Requirements
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.VideoDownload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class DownloadMappingTest {

    private val metadata = DownloadMetadata("Title", "Channel", "https://example.com/t.jpg", durationMs = 600_000)

    private fun snapshot(
        id: String = "v1",
        state: Int = Download.STATE_DOWNLOADING,
        percent: Float = 42f,
        bytes: Long = 28_000_000,
        startTimeMs: Long = 0,
    ) = DownloadSnapshot(id, state, percent, bytes, startTimeMs, metadata)

    @Test
    fun mapsEveryDownloadState() {
        assertEquals(DownloadStatus.QUEUED, downloadStatusOf(Download.STATE_QUEUED, notMetRequirements = 0))
        assertEquals(DownloadStatus.QUEUED, downloadStatusOf(Download.STATE_STOPPED, notMetRequirements = 0))
        assertEquals(DownloadStatus.DOWNLOADING, downloadStatusOf(Download.STATE_DOWNLOADING, notMetRequirements = 0))
        assertEquals(DownloadStatus.DOWNLOADING, downloadStatusOf(Download.STATE_RESTARTING, notMetRequirements = 0))
        assertEquals(DownloadStatus.COMPLETED, downloadStatusOf(Download.STATE_COMPLETED, notMetRequirements = 0))
        assertEquals(DownloadStatus.FAILED, downloadStatusOf(Download.STATE_FAILED, notMetRequirements = 0))
        assertEquals(DownloadStatus.REMOVING, downloadStatusOf(Download.STATE_REMOVING, notMetRequirements = 0))
    }

    @Test
    fun queuedWithoutANetworkIsWaitingForIt() {
        assertEquals(
            DownloadStatus.WAITING_FOR_NETWORK,
            downloadStatusOf(Download.STATE_QUEUED, notMetRequirements = Requirements.NETWORK),
        )
        // Only queued downloads wait; finished ones are unaffected by the network.
        assertEquals(
            DownloadStatus.COMPLETED,
            downloadStatusOf(Download.STATE_COMPLETED, notMetRequirements = Requirements.NETWORK),
        )
    }

    @Test
    fun mapsASnapshotWithItsMetadata() {
        assertEquals(
            VideoDownload(
                videoId = "v1",
                title = "Title",
                channelName = "Channel",
                thumbnailUrl = "https://example.com/t.jpg",
                duration = 10.minutes,
                status = DownloadStatus.DOWNLOADING,
                percent = 42f,
                bytesDownloaded = 28_000_000,
            ),
            snapshot().toVideoDownload(notMetRequirements = 0),
        )
    }

    @Test
    fun unknownPercentIsNull() {
        assertNull(snapshot(percent = C.PERCENTAGE_UNSET.toFloat()).toVideoDownload(notMetRequirements = 0).percent)
    }

    @Test
    fun completedIsAlwaysAHundredPercent() {
        val completed = snapshot(state = Download.STATE_COMPLETED, percent = C.PERCENTAGE_UNSET.toFloat())
        assertEquals(100f, completed.toVideoDownload(notMetRequirements = 0).percent)
    }

    @Test
    fun listsNewestFirst() {
        val downloads = listOf(snapshot(id = "old", startTimeMs = 1), snapshot(id = "new", startTimeMs = 2))
            .toVideoDownloads(notMetRequirements = 0)

        assertEquals(listOf("new", "old"), downloads.map { it.videoId })
    }
}
