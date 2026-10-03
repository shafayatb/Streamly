package com.shafayatb.streamly.data.video.catalog

import com.shafayatb.streamly.data.network.ApiJson
import com.shafayatb.streamly.data.video.VideoDto
import com.shafayatb.streamly.data.video.VideoListDto
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.delay

/**
 * Stands in for the Streamly API: a Ktor [MockEngine] that answers the routes a real backend
 * would, from a catalog bundled with the app. Requests still go through the real client pipeline
 * (content negotiation, DTOs, status handling), so swapping in a network engine changes nothing
 * above this class. [latency] keeps loading states visible, as they would be on a real network.
 *
 * Routes, relative to [com.shafayatb.streamly.data.network.StreamlyApi.BASE_URL]:
 * - `GET videos`: the feed, newest first.
 * - `GET videos/{id}`: one video, or 404.
 * - `GET videos/{id}/up-next`: the same category first, then the rest, newest first; 404 for an
 *   unknown id.
 * - `GET shorts`: every short, in pager order.
 */
internal class CatalogMockApi(
    catalogJson: String = BundledCatalog.JSON,
    private val shortsJson: String = BundledShorts.JSON,
    private val latency: Duration = 600.milliseconds,
) {
    private val videos: List<VideoDto> by lazy {
        ApiJson.decodeFromString<VideoListDto>(catalogJson).videos
            .sortedByDescending { Instant.parse(it.publishedAt) }
    }

    fun engine(): HttpClientEngine = MockEngine { request ->
        delay(latency)
        handle(request)
    }

    private fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        if (request.method != HttpMethod.Get) return respondError(HttpStatusCode.MethodNotAllowed)

        val path = request.url.segments.dropWhile { it != "videos" && it != "shorts" }
        return when {
            path == listOf("shorts") -> respondJson(shortsJson)
            path == listOf("videos") -> respondJson(VideoListDto(videos))
            path.size == 2 -> findVideo(path[1])
                ?.let { respondJson(it) }
                ?: respondError(HttpStatusCode.NotFound)
            path.size == 3 && path[2] == "up-next" -> findVideo(path[1])
                ?.let { respondJson(VideoListDto(upNext(it))) }
                ?: respondError(HttpStatusCode.NotFound)
            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    private fun findVideo(id: String): VideoDto? = videos.firstOrNull { it.id == id }

    private fun upNext(current: VideoDto): List<VideoDto> {
        val (sameCategory, others) = videos
            .filter { it.id != current.id }
            .partition { it.category == current.category }
        return sameCategory + others
    }

    private fun MockRequestHandleScope.respondJson(body: VideoDto): HttpResponseData =
        respondJson(ApiJson.encodeToString(VideoDto.serializer(), body))

    private fun MockRequestHandleScope.respondJson(body: VideoListDto): HttpResponseData =
        respondJson(ApiJson.encodeToString(VideoListDto.serializer(), body))

    private fun MockRequestHandleScope.respondJson(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = JsonHeaders,
    )

    private fun MockRequestHandleScope.respondError(status: HttpStatusCode): HttpResponseData =
        respond(
            content = """{"error":"${status.description}"}""",
            status = status,
            headers = JsonHeaders,
        )

    private companion object {
        val JsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
