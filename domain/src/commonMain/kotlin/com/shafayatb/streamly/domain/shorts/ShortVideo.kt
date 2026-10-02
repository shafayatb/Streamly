package com.shafayatb.streamly.domain.shorts

import com.shafayatb.streamly.domain.video.Channel

/** A short vertical clip for the Shorts pager. It loops, so it has no duration or position. */
public data class ShortVideo(
    val id: String,
    val title: String,
    val channel: Channel,
    /** HLS playlist, like every stream in the catalog. */
    val hlsUrl: String,
    val likeCount: Long,
    val commentCount: Long,
)
