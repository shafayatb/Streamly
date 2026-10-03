package com.shafayatb.streamly.testing

import com.shafayatb.streamly.domain.download.DownloadError
import com.shafayatb.streamly.domain.download.DownloadRepository
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.StorageUsage
import com.shafayatb.streamly.domain.download.VideoDownload
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow

/** A [DownloadRepository] whose downloads the test sets directly, as the downloader would report them. */
class FakeDownloadRepository : DownloadRepository {

    /** `null` while "loading": nothing is emitted until a test sets it. */
    private val result = MutableStateFlow<Result<List<VideoDownload>, DataError.Local>?>(null)

    var collections = 0
        private set

    override val downloads: Flow<Result<List<VideoDownload>, DataError.Local>> = flow {
        collections++
        emitAll(result.filterNotNull())
    }

    val storageUsage = MutableStateFlow(StorageUsage(usedBytes = 0, freeBytes = 3_100_000_000))
    override val storage: Flow<StorageUsage> = storageUsage

    /** What [download] returns. */
    var downloadResult: EmptyResult<DownloadError> = Result.Success(Unit)

    /** When set, [download] suspends until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    val requested = mutableListOf<String>()

    /** Downloads that [download] actually queued, i.e. that were not cancelled while starting. */
    val queued = mutableListOf<String>()
    val retried = mutableListOf<String>()
    val removed = mutableListOf<String>()

    fun emit(vararg downloads: VideoDownload) {
        result.value = Result.Success(downloads.toList())
    }

    fun fail(error: DataError.Local) {
        result.value = Result.Failure(error)
    }

    override suspend fun download(video: Video): EmptyResult<DownloadError> {
        requested += video.id
        gate?.await()
        if (downloadResult is Result.Success) queued += video.id
        return downloadResult
    }

    override fun retry(videoId: String) {
        retried += videoId
    }

    /** What [remove] returns. */
    var removeResult: EmptyResult<DataError.Local> = Result.Success(Unit)

    override suspend fun remove(videoId: String): EmptyResult<DataError.Local> {
        removed += videoId
        return removeResult
    }
}

fun testDownload(
    videoId: String,
    status: DownloadStatus = DownloadStatus.COMPLETED,
    percent: Float? = if (status == DownloadStatus.COMPLETED) 100f else null,
    bytesDownloaded: Long = 66_000_000,
    title: String = "Video $videoId",
    duration: Duration = 10.minutes,
): VideoDownload = VideoDownload(
    videoId = videoId,
    title = title,
    channelName = "Channel",
    thumbnailUrl = "https://example.com/$videoId.jpg",
    duration = duration,
    status = status,
    percent = percent,
    bytesDownloaded = bytesDownloaded,
)
