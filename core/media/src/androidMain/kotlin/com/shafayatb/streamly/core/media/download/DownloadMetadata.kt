package com.shafayatb.streamly.core.media.download

import com.shafayatb.streamly.domain.video.Video
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the Downloads screen shows for a download, stored in its `DownloadRequest.data` so the
 * list works offline without the catalog.
 */
@Serializable
internal data class DownloadMetadata(
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationMs: Long,
) {
    fun encode(): ByteArray = json.encodeToString(serializer(), this).encodeToByteArray()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(video: Video): DownloadMetadata = DownloadMetadata(
            title = video.title,
            channelName = video.channel.name,
            thumbnailUrl = video.thumbnailUrl,
            durationMs = video.duration?.inWholeMilliseconds ?: 0,
        )

        /** Anything unreadable still lists the download, under its video id. */
        fun decode(data: ByteArray, videoId: String): DownloadMetadata =
            runCatching { json.decodeFromString(serializer(), data.decodeToString()) }
                .getOrElse { DownloadMetadata(title = videoId, channelName = "", thumbnailUrl = "", durationMs = 0) }
    }
}
