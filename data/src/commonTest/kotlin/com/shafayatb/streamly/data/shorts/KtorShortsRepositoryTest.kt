package com.shafayatb.streamly.data.shorts

import com.shafayatb.streamly.data.network.createApiHttpClient
import com.shafayatb.streamly.data.video.catalog.CatalogMockApi
import com.shafayatb.streamly.domain.shorts.ShortVideo
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Channel
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

class KtorShortsRepositoryTest {

    private fun repository(engine: HttpClientEngine) = KtorShortsRepository(createApiHttpClient(engine))

    private val catalogRepository = repository(CatalogMockApi(latency = Duration.ZERO).engine())

    @Test
    fun servesTheBundledShortsInPagerOrder() = runTest {
        val shorts = assertIs<Result.Success<List<ShortVideo>>>(catalogRepository.getShorts()).data

        assertEquals(8, shorts.size)
        assertEquals(shorts.size, shorts.map { it.id }.toSet().size)
        assertTrue(shorts.all { it.hlsUrl.startsWith("https://") && it.hlsUrl.endsWith(".m3u8") })
        assertEquals(
            ShortVideo(
                id = "night-mode-home-screen",
                title = "This home screen setup is unreal",
                channel = Channel(id = "pixellab", name = "Pixel Lab"),
                hlsUrl = "https://69e664383f19480597c5a256--twg-video-feed-demo.netlify.app/v0/index.m3u8",
                likeCount = 48_200,
                commentCount = 1_310,
            ),
            shorts.first(),
        )
    }

    @Test
    fun missingFieldMapsToSerialization() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"shorts": [{"id": "s1", "title": "T"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        assertEquals(Result.Failure(DataError.Network.SERIALIZATION), repository(engine).getShorts())
    }

    @Test
    fun ioFailureMapsToNoInternet() = runTest {
        val result = repository(MockEngine { throw IOException("Unable to resolve host") }).getShorts()

        assertEquals(Result.Failure(DataError.Network.NO_INTERNET), result)
    }
}
