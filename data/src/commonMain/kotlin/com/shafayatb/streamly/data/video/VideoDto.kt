package com.shafayatb.streamly.data.video

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class VideoListDto(
    val videos: List<VideoDto>,
)

@Serializable
internal data class VideoDto(
    val id: String,
    val title: String,
    val description: String,
    val channel: ChannelDto,
    val thumbnailUrl: String,
    val hlsUrl: String,
    /** Absent for live streams. */
    val durationSeconds: Long? = null,
    val viewCount: Long,
    /** ISO-8601 instant, e.g. `2026-09-28T16:30:00Z`. */
    val publishedAt: String,
    val category: CategoryDto,
)

@Serializable
internal data class ChannelDto(
    val id: String,
    val name: String,
)

@Serializable
internal enum class CategoryDto {
    @SerialName("music")
    MUSIC,

    @SerialName("film")
    FILM,

    @SerialName("tech")
    TECH,
}
