package com.shafayatb.streamly.core.media.download

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadMetadataTest {

    @Test
    fun roundTripsThroughTheRequestData() {
        val metadata = DownloadMetadata(
            title = "Media3 in 10 minutes",
            channelName = "CodeLabs",
            thumbnailUrl = "https://example.com/t.jpg",
            durationMs = 600_000,
        )

        assertEquals(metadata, DownloadMetadata.decode(metadata.encode(), videoId = "v1"))
    }

    @Test
    fun unreadableDataFallsBackToTheVideoId() {
        val fallback = DownloadMetadata(title = "v1", channelName = "", thumbnailUrl = "", durationMs = 0)

        assertEquals(fallback, DownloadMetadata.decode(ByteArray(0), videoId = "v1"))
        assertEquals(fallback, DownloadMetadata.decode("not json".encodeToByteArray(), videoId = "v1"))
    }
}
