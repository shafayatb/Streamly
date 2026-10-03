package com.shafayatb.streamly.core.media.download

import androidx.media3.exoplayer.scheduler.Requirements
import com.shafayatb.streamly.domain.settings.DownloadQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadPolicyTest {

    @Test
    fun anyNetworkRequiresOnlyANetwork() {
        val requirements = requirementsFor(wifiOnly = false)

        assertTrue(requirements.isNetworkRequired)
        assertFalse(requirements.isUnmeteredNetworkRequired)
    }

    @Test
    fun wifiOnlyRequiresAnUnmeteredNetwork() {
        val requirements = requirementsFor(wifiOnly = true)

        assertTrue(requirements.isUnmeteredNetworkRequired)
        assertEquals(Requirements(Requirements.NETWORK_UNMETERED), requirements)
    }

    @Test
    fun eachQualityCapsTheVideoSize() {
        assertEquals(VideoSizeCap(640, 360), DownloadQuality.DATA_SAVER.maxVideoSize())
        assertEquals(VideoSizeCap(854, 480), DownloadQuality.STANDARD.maxVideoSize())
        assertEquals(VideoSizeCap(1280, 720), DownloadQuality.HIGH.maxVideoSize())
    }
}
