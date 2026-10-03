package com.shafayatb.streamly.domain.download

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import kotlinx.coroutines.flow.Flow

/** Which accounts saved each downloaded video, keyed by video id and account key. */
public interface DownloadOwnershipRepository {
    public val owners: Flow<Result<Map<String, Set<String>>, DataError.Local>>

    public suspend fun addOwner(videoId: String, account: String): EmptyResult<DataError.Local>

    /**
     * Removes [account] from [videoId]'s owners and returns the owners left, read in the same
     * write, so "was that the last owner?" cannot race another change.
     */
    public suspend fun removeOwner(videoId: String, account: String): Result<Set<String>, DataError.Local>
}
