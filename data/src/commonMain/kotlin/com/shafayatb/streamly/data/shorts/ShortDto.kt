package com.shafayatb.streamly.data.shorts

import com.shafayatb.streamly.data.video.ChannelDto
import kotlinx.serialization.Serializable

@Serializable
internal data class ShortListDto(
    val shorts: List<ShortDto>,
)

@Serializable
internal data class ShortDto(
    val id: String,
    val title: String,
    val channel: ChannelDto,
    val hlsUrl: String,
    val likeCount: Long,
    val commentCount: Long,
)
