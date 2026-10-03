package com.shafayatb.streamly.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal object StreamlyApi {
    const val BASE_URL: String = "https://api.streamly.app/v1/"
}

internal val ApiJson: Json = Json {
    ignoreUnknownKeys = true
}

/** The one client configuration for the Streamly API, whichever [engine] serves it. */
internal fun createApiHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(ApiJson)
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
    }
    defaultRequest {
        url(StreamlyApi.BASE_URL)
    }
}
