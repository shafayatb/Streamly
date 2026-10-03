package com.shafayatb.streamly.testing

import com.shafayatb.streamly.domain.history.WatchHistoryEntry
import com.shafayatb.streamly.domain.history.WatchHistoryRepository
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow

class FakeWatchHistoryRepository : WatchHistoryRepository {

    /** `null` while the history is still being read. */
    val result = MutableStateFlow<Result<List<WatchHistoryEntry>, DataError.Local>?>(Result.Success(emptyList()))

    /** When set, the next collection emits this failure and completes, as DataStore's `data` with `catch` does. */
    var failNextRead: DataError.Local? = null

    override val entries: Flow<Result<List<WatchHistoryEntry>, DataError.Local>> = flow {
        val failure = failNextRead
        if (failure != null) {
            failNextRead = null
            emit(Result.Failure(failure))
        } else {
            emitAll(result.filterNotNull())
        }
    }

    val resumePositions = mutableMapOf<String, Duration>()

    /** When set, [resumePosition] suspends until it completes. */
    var resumeGate: CompletableDeferred<Unit>? = null

    /** [inserts] is true for [record], which adds the video, and false for [updateProgress]. */
    data class Record(val videoId: String, val position: Duration, val duration: Duration?, val inserts: Boolean = true)

    val records = mutableListOf<Record>()
    val removed = mutableListOf<String>()
    var clears = 0
        private set

    /** When set, [remove] and [clear] fail with it. */
    var writeFailure: DataError.Local? = null

    override suspend fun resumePosition(videoId: String): Duration {
        resumeGate?.await()
        return resumePositions[videoId] ?: Duration.ZERO
    }

    override fun record(video: Video, position: Duration, duration: Duration?) {
        records += Record(video.id, position, duration, inserts = true)
    }

    override fun updateProgress(video: Video, position: Duration, duration: Duration?) {
        records += Record(video.id, position, duration, inserts = false)
    }

    override suspend fun remove(videoId: String): EmptyResult<DataError.Local> {
        writeFailure?.let { return Result.Failure(it) }
        removed += videoId
        return Result.Success(Unit)
    }

    override suspend fun clear(): EmptyResult<DataError.Local> {
        writeFailure?.let { return Result.Failure(it) }
        clears++
        return Result.Success(Unit)
    }
}
