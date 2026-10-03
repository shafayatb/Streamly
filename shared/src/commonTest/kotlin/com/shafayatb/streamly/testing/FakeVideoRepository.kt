package com.shafayatb.streamly.testing

import com.shafayatb.streamly.domain.shorts.ShortVideo
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Category
import com.shafayatb.streamly.domain.video.Channel
import com.shafayatb.streamly.domain.video.Video
import com.shafayatb.streamly.domain.video.VideoRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred

class FakeVideoRepository(
    var videos: List<Video> = emptyList(),
) : VideoRepository {

    /** When set, every call fails with this error. */
    var failure: DataError.Network? = null

    /** When set, only [getUpNext] fails, with this error. */
    var upNextFailure: DataError.Network? = null

    /** When set, calls suspend until it completes, so tests can observe in-flight state. */
    var gate: CompletableDeferred<Unit>? = null

    var feedRequests = 0
        private set

    override suspend fun getFeed(): Result<List<Video>, DataError.Network> {
        feedRequests++
        return respond { videos }
    }

    override suspend fun getVideo(id: String): Result<Video, DataError.Network> {
        gate?.await()
        failure?.let { return Result.Failure(it) }
        return videos.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Failure(DataError.Network.NOT_FOUND)
    }

    override suspend fun getUpNext(id: String): Result<List<Video>, DataError.Network> {
        upNextFailure?.let { return Result.Failure(it) }
        return respond { videos.filter { it.id != id } }
    }

    private suspend fun <T> respond(data: () -> T): Result<T, DataError.Network> {
        gate?.await()
        failure?.let { return Result.Failure(it) }
        return Result.Success(data())
    }
}

fun testVideo(
    id: String,
    title: String = "Video $id",
    channelName: String = "Channel",
    category: Category = Category.TECH,
    duration: Duration? = 10.minutes,
    viewCount: Long = 1_000,
    publishedAt: Instant = Instant.parse("2026-09-01T00:00:00Z"),
): Video = Video(
    id = id,
    title = title,
    description = "About $title",
    channel = Channel(id = channelName.lowercase(), name = channelName),
    thumbnailUrl = "https://example.com/$id.jpg",
    hlsUrl = "https://example.com/$id.m3u8",
    duration = duration,
    viewCount = viewCount,
    publishedAt = publishedAt,
    category = category,
)

fun testShort(
    id: String,
    title: String = "Short $id",
    channelId: String = "channel",
    likeCount: Long = 1_200,
    commentCount: Long = 30,
): ShortVideo = ShortVideo(
    id = id,
    title = title,
    channel = Channel(id = channelId, name = "Channel"),
    hlsUrl = "https://example.com/$id.m3u8",
    likeCount = likeCount,
    commentCount = commentCount,
)
