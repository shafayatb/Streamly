package com.shafayatb.streamly.data.video

import com.shafayatb.streamly.data.network.safeCall
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import com.shafayatb.streamly.domain.video.VideoRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.encodeURLPathPart

internal class KtorVideoRepository(
    private val client: HttpClient,
) : VideoRepository {

    override suspend fun getFeed(): Result<List<Video>, DataError.Network> =
        safeCall<VideoListDto, List<Video>>(
            execute = { client.get("videos") },
            transform = { body -> body.videos.map { it.toVideo() } },
        )

    override suspend fun getVideo(id: String): Result<Video, DataError.Network> =
        safeCall<VideoDto, Video>(
            execute = { client.get("videos/${id.encodeURLPathPart()}") },
            transform = { it.toVideo() },
        )

    override suspend fun getUpNext(id: String): Result<List<Video>, DataError.Network> =
        safeCall<VideoListDto, List<Video>>(
            execute = { client.get("videos/${id.encodeURLPathPart()}/up-next") },
            transform = { body -> body.videos.map { it.toVideo() } },
        )
}
