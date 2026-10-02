package com.shafayatb.streamly.domain.download

import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Category
import com.shafayatb.streamly.domain.video.Channel
import com.shafayatb.streamly.domain.video.Video
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow

class FakeDeviceDownloads : DeviceDownloads {
    private val result = MutableStateFlow<Result<List<VideoDownload>, DataError.Local>>(Result.Success(emptyList()))
    override val downloads: Flow<Result<List<VideoDownload>, DataError.Local>> = result

    val storageUsage = MutableStateFlow(StorageUsage(usedBytes = 0, freeBytes = 5_000))
    override val storage: Flow<StorageUsage> = storageUsage

    var downloadResult: EmptyResult<DownloadError> = Result.Success(Unit)

    /** When set, [download] suspends until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    val requested = mutableListOf<String>()
    val queued = mutableListOf<String>()
    val retried = mutableListOf<String>()
    val removed = mutableListOf<String>()

    fun set(vararg downloads: VideoDownload) {
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

    override fun remove(videoId: String) {
        removed += videoId
    }
}

class FakeDownloadOwnership : DownloadOwnershipRepository {
    val stored = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** `false` until the store has been "read": [owners] emits nothing before then. */
    val loaded = MutableStateFlow(true)
    val readFailure = MutableStateFlow<DataError.Local?>(null)
    var writeFailure: DataError.Local? = null

    /**
     * When set, the next collection emits this failure and completes, as DataStore's `data` with
     * `catch` does after a read error; later collections read normally.
     */
    var failNextRead: DataError.Local? = null

    private val live: Flow<Result<Map<String, Set<String>>, DataError.Local>> =
        combine(stored, loaded, readFailure) { stored, loaded, failure ->
            when {
                !loaded -> null
                failure != null -> Result.Failure(failure)
                else -> Result.Success(stored)
            }
        }.filterNotNull()

    override val owners: Flow<Result<Map<String, Set<String>>, DataError.Local>> = flow {
        val failure = failNextRead
        if (failure != null) {
            failNextRead = null
            emit(Result.Failure(failure))
        } else {
            emitAll(live)
        }
    }

    override suspend fun addOwner(videoId: String, account: String): EmptyResult<DataError.Local> {
        writeFailure?.let { return Result.Failure(it) }
        stored.value += videoId to (stored.value[videoId].orEmpty() + account)
        return Result.Success(Unit)
    }

    override suspend fun removeOwner(videoId: String, account: String): Result<Set<String>, DataError.Local> {
        writeFailure?.let { return Result.Failure(it) }
        val remaining = stored.value[videoId].orEmpty() - account
        stored.value = if (remaining.isEmpty()) stored.value - videoId else stored.value + (videoId to remaining)
        return Result.Success(remaining)
    }
}

class FakeSessions(initial: Session?) : SessionRepository {
    override val session = MutableStateFlow(initial)
    override suspend fun signInWithGoogle(): EmptyResult<DataError.Local> = error("Not used")
    override suspend fun signInWithEmail(email: String): EmptyResult<DataError.Local> = error("Not used")
    override suspend fun continueAsGuest(): EmptyResult<DataError.Local> = error("Not used")
    override suspend fun signOut(): EmptyResult<DataError.Local> = error("Not used")
}

fun download(
    videoId: String,
    status: DownloadStatus = DownloadStatus.COMPLETED,
    bytes: Long = 100,
): VideoDownload = VideoDownload(
    videoId = videoId,
    title = "Video $videoId",
    channelName = "Channel",
    thumbnailUrl = "",
    duration = 10.minutes,
    status = status,
    percent = if (status == DownloadStatus.COMPLETED) 100f else null,
    bytesDownloaded = bytes,
)

fun video(id: String, duration: Duration? = 10.minutes): Video = Video(
    id = id,
    title = "Video $id",
    description = "",
    channel = Channel(id = "c", name = "Channel"),
    thumbnailUrl = "",
    hlsUrl = "https://example.com/$id.m3u8",
    duration = duration,
    viewCount = 0,
    publishedAt = Instant.fromEpochMilliseconds(0),
    category = Category.TECH,
)
