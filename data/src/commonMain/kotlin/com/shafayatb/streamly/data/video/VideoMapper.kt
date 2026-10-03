package com.shafayatb.streamly.data.video

import com.shafayatb.streamly.domain.video.Category
import com.shafayatb.streamly.domain.video.Channel
import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** @throws IllegalArgumentException when [VideoDto.publishedAt] is not an ISO-8601 instant. */
internal fun VideoDto.toVideo(): Video = Video(
    id = id,
    title = title,
    description = description,
    channel = Channel(id = channel.id, name = channel.name),
    thumbnailUrl = thumbnailUrl,
    hlsUrl = hlsUrl,
    duration = durationSeconds?.seconds,
    viewCount = viewCount,
    publishedAt = Instant.parse(publishedAt),
    category = category.toCategory(),
)

private fun CategoryDto.toCategory(): Category = when (this) {
    CategoryDto.MUSIC -> Category.MUSIC
    CategoryDto.FILM -> Category.FILM
    CategoryDto.TECH -> Category.TECH
}
