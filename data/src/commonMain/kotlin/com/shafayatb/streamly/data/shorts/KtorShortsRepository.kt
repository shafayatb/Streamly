package com.shafayatb.streamly.data.shorts

import com.shafayatb.streamly.data.network.safeCall
import com.shafayatb.streamly.domain.shorts.ShortVideo
import com.shafayatb.streamly.domain.shorts.ShortsRepository
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.request.get

internal class KtorShortsRepository(
    private val client: HttpClient,
) : ShortsRepository {

    override suspend fun getShorts(): Result<List<ShortVideo>, DataError.Network> =
        safeCall<ShortListDto, List<ShortVideo>>(
            execute = { client.get("shorts") },
            transform = { body -> body.shorts.map { it.toShortVideo() } },
        )
}
