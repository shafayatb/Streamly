package com.shafayatb.streamly.home

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.formatAge
import com.shafayatb.streamly.core.presentation.formatDuration
import com.shafayatb.streamly.core.presentation.formatViewCount
import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Instant

@Immutable
data class VideoCardUi(
    val id: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val views: UiText,
    val age: UiText,
    /** "10:34"; `null` for a live stream, which shows a live badge instead. */
    val duration: String?,
) {
    val isLive: Boolean get() = duration == null
}

/** [now] is passed in rather than read here so ages are testable and consistent across a list. */
fun Video.toCardUi(now: Instant): VideoCardUi = VideoCardUi(
    id = id,
    title = title,
    channelName = channel.name,
    thumbnailUrl = thumbnailUrl,
    views = formatViewCount(viewCount),
    age = formatAge(publishedAt, now),
    duration = duration?.let(::formatDuration),
)
