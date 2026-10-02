package com.shafayatb.streamly.testing

import com.shafayatb.streamly.domain.shorts.ShortVideo
import com.shafayatb.streamly.domain.shorts.ShortsRepository
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import kotlinx.coroutines.CompletableDeferred

class FakeShortsRepository(
    var shorts: List<ShortVideo> = emptyList(),
) : ShortsRepository {

    /** When set, every call fails with this error. */
    var failure: DataError.Network? = null

    /** When set, calls suspend until it completes, so tests can observe in-flight state. */
    var gate: CompletableDeferred<Unit>? = null

    var requests = 0
        private set

    override suspend fun getShorts(): Result<List<ShortVideo>, DataError.Network> {
        requests++
        gate?.await()
        failure?.let { return Result.Failure(it) }
        return Result.Success(shorts)
    }
}
