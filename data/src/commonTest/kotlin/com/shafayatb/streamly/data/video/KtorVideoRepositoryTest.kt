package com.shafayatb.streamly.data.video

import com.shafayatb.streamly.data.network.createApiHttpClient
import com.shafayatb.streamly.data.video.catalog.CatalogMockApi
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Category
import com.shafayatb.streamly.domain.video.Channel
import com.shafayatb.streamly.domain.video.Video
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class KtorVideoRepositoryTest {

    private fun repository(engine: HttpClientEngine) = KtorVideoRepository(createApiHttpClient(engine))

    private val catalogRepository = repository(CatalogMockApi(latency = Duration.ZERO).engine())

    private fun respondingWith(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        MockEngine {
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }

    @Test
    fun feedMapsTheBundledCatalogNewestFirst() = runTest {
        val feed = assertIs<Result.Success<List<Video>>>(catalogRepository.getFeed()).data

        assertEquals(10, feed.size)
        assertEquals(feed.sortedByDescending { it.publishedAt }, feed)
        assertEquals(feed.size, feed.map { it.id }.toSet().size)
        assertTrue(feed.all { it.hlsUrl.startsWith("https://") && it.hlsUrl.contains(".m3u8") })
        assertTrue(feed.all { it.thumbnailUrl.startsWith("https://") })
    }

    @Test
    fun videoMapsEveryField() = runTest {
        val video = assertIs<Result.Success<Video>>(catalogRepository.getVideo("media3-in-10-minutes")).data

        assertEquals("Media3 in 10 minutes", video.title)
        assertEquals(Channel(id = "codelabs", name = "CodeLabs"), video.channel)
        assertEquals(600.seconds, video.duration)
        assertEquals(44_100, video.viewCount)
        assertEquals(Instant.parse("2026-09-21T09:00:00Z"), video.publishedAt)
        assertEquals(Category.TECH, video.category)
    }

    @Test
    fun liveStreamHasNoDuration() = runTest {
        val video = assertIs<Result.Success<Video>>(catalogRepository.getVideo("studio-feed-live")).data

        assertNull(video.duration)
        assertTrue(video.isLive)
    }

    @Test
    fun unknownVideoIsNotFound() = runTest {
        assertEquals(Result.Failure(DataError.Network.NOT_FOUND), catalogRepository.getVideo("missing"))
        assertEquals(Result.Failure(DataError.Network.NOT_FOUND), catalogRepository.getUpNext("missing"))
    }

    @Test
    fun upNextExcludesTheCurrentVideoAndPutsItsCategoryFirst() = runTest {
        val upNext = assertIs<Result.Success<List<Video>>>(catalogRepository.getUpNext("synthwave-session")).data

        assertEquals(9, upNext.size)
        assertTrue(upNext.none { it.id == "synthwave-session" })
        val musicCount = upNext.count { it.category == Category.MUSIC }
        assertTrue(upNext.take(musicCount).all { it.category == Category.MUSIC })
    }

    @Test
    fun httpServerErrorMapsToServerError() = runTest {
        val result = repository(respondingWith("""{"error":"boom"}""", HttpStatusCode.InternalServerError)).getFeed()

        assertEquals(Result.Failure(DataError.Network.SERVER_ERROR), result)
    }

    @Test
    fun malformedJsonMapsToSerialization() = runTest {
        val result = repository(respondingWith("""{"videos": [{"id": 42""")).getFeed()

        assertEquals(Result.Failure(DataError.Network.SERIALIZATION), result)
    }

    @Test
    fun unknownCategoryMapsToSerialization() = runTest {
        val result = repository(respondingWith(videoJson(category = "cooking"))).getVideo("v1")

        assertEquals(Result.Failure(DataError.Network.SERIALIZATION), result)
    }

    @Test
    fun invalidTimestampMapsToSerialization() = runTest {
        val result = repository(respondingWith(videoJson(publishedAt = "last tuesday"))).getVideo("v1")

        assertEquals(Result.Failure(DataError.Network.SERIALIZATION), result)
    }

    @Test
    fun ioFailureMapsToNoInternet() = runTest {
        val result = repository(MockEngine { throw IOException("Unable to resolve host") }).getFeed()

        assertEquals(Result.Failure(DataError.Network.NO_INTERNET), result)
    }

    @Test
    fun timeoutMapsToRequestTimeout() = runTest {
        val result = repository(MockEngine { throw HttpRequestTimeoutException("videos", 15_000) }).getFeed()

        assertEquals(Result.Failure(DataError.Network.REQUEST_TIMEOUT), result)
    }

    private fun videoJson(
        category: String = "tech",
        publishedAt: String = "2026-09-21T09:00:00Z",
    ) = """
        {
          "id": "v1", "title": "T", "description": "D",
          "channel": { "id": "c", "name": "C" },
          "thumbnailUrl": "https://example.com/t.jpg", "hlsUrl": "https://example.com/v.m3u8",
          "durationSeconds": 10, "viewCount": 1,
          "publishedAt": "$publishedAt", "category": "$category"
        }
    """.trimIndent()
}
