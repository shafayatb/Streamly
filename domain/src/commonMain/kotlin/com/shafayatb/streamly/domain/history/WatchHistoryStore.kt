package com.shafayatb.streamly.domain.history

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import kotlinx.coroutines.flow.Flow

/** Persists each account's watch history, newest first. [account] is a `Session.accountKey()`. */
public interface WatchHistoryStore {
    public fun entries(account: String): Flow<Result<List<WatchHistoryEntry>, DataError.Local>>

    /** Puts [entry] at the top, replacing any entry for the same video, and keeps the newest 100. */
    public suspend fun upsert(account: String, entry: WatchHistoryEntry): EmptyResult<DataError.Local>

    /** Like [upsert], but does nothing when the account no longer has the video (removed or cleared). */
    public suspend fun update(account: String, entry: WatchHistoryEntry): EmptyResult<DataError.Local>

    public suspend fun remove(account: String, videoId: String): EmptyResult<DataError.Local>

    public suspend fun clear(account: String): EmptyResult<DataError.Local>
}
