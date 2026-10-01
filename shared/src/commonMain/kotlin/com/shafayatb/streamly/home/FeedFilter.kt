package com.shafayatb.streamly.home

import com.shafayatb.streamly.domain.video.Category
import com.shafayatb.streamly.domain.video.Video

/** The home feed's chips. Filtering is local: the whole feed is already loaded. */
enum class FeedFilter {
    ALL,
    MUSIC,
    LIVE;

    fun matches(video: Video): Boolean = when (this) {
        ALL -> true
        MUSIC -> video.category == Category.MUSIC
        LIVE -> video.isLive
    }
}
