package com.shafayatb.streamly.domain.history

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow

/** The signed-in account's watch history. */
public interface WatchHistoryRepository {
    /** Newest first; empty while nobody is signed in. */
    public val entries: Flow<Result<List<WatchHistoryEntry>, DataError.Local>>

    /** Where [videoId] should start for this account: zero without a usable entry or when the history cannot be read. */
    public suspend fun resumePosition(videoId: String): Duration

    /**
     * Records that [video] started playing at [position], adding it or moving it to the top.
     * [duration] is the player's length when known, which can differ slightly from the catalog's.
     * The write happens in the background and outlives the caller; a failure is dropped.
     */
    public fun record(video: Video, position: Duration, duration: Duration? = video.duration)

    /**
     * Saves how far [video] has got since [record], like [record] but never adding it back: a video
     * the user removed (or cleared) while it was still saving stays removed.
     */
    public fun updateProgress(video: Video, position: Duration, duration: Duration? = video.duration)

    public suspend fun remove(videoId: String): EmptyResult<DataError.Local>

    public suspend fun clear(): EmptyResult<DataError.Local>
}
