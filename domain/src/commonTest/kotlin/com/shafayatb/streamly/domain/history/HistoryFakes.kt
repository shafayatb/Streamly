package com.shafayatb.streamly.domain.history

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory [WatchHistoryStore] with the real store's ordering rules. */
class FakeWatchHistoryStore : WatchHistoryStore {
    val stored = MutableStateFlow<Map<String, List<WatchHistoryEntry>>>(emptyMap())
    var readFailure: DataError.Local? = null

    /** When set, [upsert] suspends until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    override fun entries(account: String): Flow<Result<List<WatchHistoryEntry>, DataError.Local>> =
        stored.map { all ->
            readFailure?.let { Result.Failure(it) } ?: Result.Success(all[account].orEmpty())
        }

    override suspend fun upsert(account: String, entry: WatchHistoryEntry): EmptyResult<DataError.Local> {
        gate?.await()
        val others = stored.value[account].orEmpty().filter { it.videoId != entry.videoId }
        stored.value += account to (listOf(entry) + others)
        return Result.Success(Unit)
    }

    override suspend fun update(account: String, entry: WatchHistoryEntry): EmptyResult<DataError.Local> {
        if (stored.value[account].orEmpty().none { it.videoId == entry.videoId }) return Result.Success(Unit)
        return upsert(account, entry)
    }

    override suspend fun remove(account: String, videoId: String): EmptyResult<DataError.Local> {
        stored.value += account to stored.value[account].orEmpty().filter { it.videoId != videoId }
        return Result.Success(Unit)
    }

    override suspend fun clear(account: String): EmptyResult<DataError.Local> {
        stored.value -= account
        return Result.Success(Unit)
    }
}
