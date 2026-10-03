package com.shafayatb.streamly.data.shorts

import com.shafayatb.streamly.domain.shorts.ShortVideo
import com.shafayatb.streamly.domain.video.Channel

internal fun ShortDto.toShortVideo(): ShortVideo = ShortVideo(
    id = id,
    title = title,
    channel = Channel(id = channel.id, name = channel.name),
    hlsUrl = hlsUrl,
    likeCount = likeCount,
    commentCount = commentCount,
)
