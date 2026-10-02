package com.shafayatb.streamly.domain.download

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Error
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import kotlinx.coroutines.flow.Flow

/**
 * Videos saved for offline playback. Downloads continue in the background and survive app
 * restarts. Call it from the main thread.
 */
public interface DownloadRepository {
    /**
     * Every download, newest first, updated as their progress changes. Nothing is emitted until
     * the stored downloads have been read, so a collector is loading until the first value.
     * Collecting again after a [Result.Failure] reads them again.
     */
    public val downloads: Flow<Result<List<VideoDownload>, DataError.Local>>

    public val storage: Flow<StorageUsage>

    /**
     * Reads [video]'s playlist, picks the rendition to save, and queues the download. Returns
     * once it is queued, not when it finishes; progress then arrives through [downloads]. The
     * start continues if the caller stops waiting; only [remove] cancels it.
     */
    public suspend fun download(video: Video): EmptyResult<DownloadError>

    /** Starts a [DownloadStatus.FAILED] download again. */
    public fun retry(videoId: String)

    /**
     * Cancels an unfinished download, including one [download] is still preparing, or deletes a
     * finished one and frees its space.
     */
    public fun remove(videoId: String)
}

public enum class DownloadError : Error {
    /** The playlist could not be fetched, e.g. while offline. */
    NETWORK,

    /** Live streams have no end, so they cannot be saved. */
    LIVE_NOT_SUPPORTED,
    UNKNOWN,
}
