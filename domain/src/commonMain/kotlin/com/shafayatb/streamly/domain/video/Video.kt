package com.shafayatb.streamly.domain.video

import kotlin.time.Duration
import kotlin.time.Instant

public data class Video(
    val id: String,
    val title: String,
    val description: String,
    val channel: Channel,
    val thumbnailUrl: String,
    /** HLS master playlist; every video in the catalog streams over HLS. */
    val hlsUrl: String,
    /** `null` for live streams, which have no fixed length. */
    val duration: Duration?,
    val viewCount: Long,
    val publishedAt: Instant,
    val category: Category,
) {
    val isLive: Boolean get() = duration == null
}

public data class Channel(
    val id: String,
    val name: String,
)

public enum class Category {
    MUSIC,
    FILM,
    TECH,
}
