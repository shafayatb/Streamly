package com.shafayatb.streamly.core.media.download

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.shafayatb.streamly.core.media.cache.MediaCache
import com.shafayatb.streamly.domain.download.DeviceDownloads
import com.shafayatb.streamly.domain.download.DownloadError
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.StorageUsage
import com.shafayatb.streamly.domain.download.VideoDownload
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The app's only [DownloadManager]. It writes into [MediaCache]'s cache, which playback reads
 * first, so a finished download plays offline through the normal player.
 *
 * `DownloadManager` pushes state changes but not progress, so progress is polled while anything
 * is downloading. Call it from the main thread, the manager's own thread.
 */
@OptIn(UnstableApi::class)
internal class MediaDownloads(
    context: Context,
    mediaCache: MediaCache,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DeviceDownloads, OfflineMediaItems {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val upstreamDataSourceFactory = mediaCache.upstreamDataSourceFactory

    val downloadManager: DownloadManager = DownloadManager(
        appContext,
        mediaCache.databaseProvider,
        mediaCache.cache,
        upstreamDataSourceFactory,
        Executors.newFixedThreadPool(DOWNLOAD_THREADS),
    )

    private val helperFactory = DownloadHelper.Factory()
        .setDataSourceFactory(upstreamDataSourceFactory)
        .setRenderersFactory(DefaultRenderersFactory(appContext))
        .setTrackSelectionParameters(
            // Download defaults force the highest bitrate; the size cap makes that the best
            // variant up to 480p. Streams without one fall back to their lowest variant.
            DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS.buildUpon()
                .setMaxVideoSize(MAX_VIDEO_WIDTH, MAX_VIDEO_HEIGHT)
                .build(),
        )

    /** `null` until the download index has been read. */
    private val snapshots = MutableStateFlow<Map<String, DownloadSnapshot>?>(null)
    private val requests = mutableMapOf<String, DownloadRequest>()
    private val notMetRequirements = MutableStateFlow(downloadManager.notMetRequirements)

    // Changes reported while the index is being read; applied on top of it afterwards.
    private val pendingChanges = linkedMapOf<String, Download?>()
    private val loadLock = Mutex()
    private var progressPolling: Job? = null

    // Downloads still reading their playlist, so remove() can cancel them before they are queued.
    private val pendingStarts = mutableMapOf<String, Deferred<EmptyResult<DownloadError>>>()

    override val downloads: Flow<Result<List<VideoDownload>, DataError.Local>> = flow {
        val error = ensureLoaded()
        if (error != null) {
            emit(Result.Failure(error))
            return@flow
        }
        emitAll(
            combine(snapshots.filterNotNull(), notMetRequirements) { snapshots, notMet ->
                Result.Success(snapshots.values.toVideoDownloads(notMet))
            }.distinctUntilChanged(),
        )
    }

    override val storage: Flow<StorageUsage> = snapshots.filterNotNull()
        .map { snapshots ->
            StorageUsage(
                usedBytes = snapshots.values
                    .filter { it.state != Download.STATE_REMOVING }
                    .sumOf { it.bytesDownloaded },
                freeBytes = appContext.filesDir.usableSpace,
            )
        }
        .distinctUntilChanged()
        .flowOn(ioDispatcher)

    init {
        downloadManager.addListener(ManagerListener())
        scope.launch { ensureLoaded() }
    }

    override suspend fun download(video: Video): EmptyResult<DownloadError> {
        if (video.isLive) return Result.Failure(DownloadError.LIVE_NOT_SUPPORTED)
        // Prepared in this object's scope, so leaving the screen does not drop a download the
        // user started; remove() cancels it instead.
        val start = scope.async { prepareAndQueue(video) }
        pendingStarts[video.id] = start
        start.invokeOnCompletion { if (pendingStarts[video.id] === start) pendingStarts -= video.id }
        return start.await()
    }

    private suspend fun prepareAndQueue(video: Video): EmptyResult<DownloadError> {
        val helper = helperFactory.create(
            MediaItem.Builder()
                .setMediaId(video.id)
                .setUri(video.hlsUrl)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build(),
        )
        return try {
            helper.prepareAndAwait()
            val request = helper.getDownloadRequest(video.id, DownloadMetadata.of(video).encode())
            DownloadService.sendAddDownload(appContext, StreamlyDownloadService::class.java, request, false)
            Result.Success(Unit)
        } catch (e: DownloadHelper.LiveContentUnsupportedException) {
            Result.Failure(DownloadError.LIVE_NOT_SUPPORTED)
        } catch (e: IOException) {
            Log.w(TAG, "Could not prepare ${video.id}", e)
            Result.Failure(DownloadError.NETWORK)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // For example the service refusing to start; the user gets a message, not a crash.
            Log.e(TAG, "Could not queue ${video.id}", e)
            Result.Failure(DownloadError.UNKNOWN)
        } finally {
            helper.release()
        }
    }

    override fun retry(videoId: String) {
        val request = requests[videoId] ?: return
        // Adding a request again restarts a download that failed.
        DownloadService.sendAddDownload(appContext, StreamlyDownloadService::class.java, request, false)
    }

    override fun remove(videoId: String) {
        pendingStarts.remove(videoId)?.cancel()
        DownloadService.sendRemoveDownload(appContext, StreamlyDownloadService::class.java, videoId, false)
    }

    override fun completedMediaItem(videoId: String): MediaItem? {
        val loaded = snapshots.value
        val request = if (loaded != null) {
            requests[videoId].takeIf { loaded[videoId]?.state == Download.STATE_COMPLETED }
        } else {
            // Only before the first index read finishes, e.g. a player screen restored at launch:
            // one indexed row, so reading it here is cheaper than playing the wrong rendition.
            downloadManager.downloadIndex.getDownload(videoId)
                ?.takeIf { it.state == Download.STATE_COMPLETED }
                ?.request
        }
        return request?.toMediaItem()?.also { Log.d(TAG, "Playing download $videoId (${it.localConfiguration?.streamKeys?.size} stream keys)") }
    }

    fun release() {
        scope.cancel()
        downloadManager.release()
    }

    /** Reads the index once; returns the failure, if any, so a later collector can try again. */
    private suspend fun ensureLoaded(): DataError.Local? = loadLock.withLock {
        if (snapshots.value != null) return null
        val stored: List<Download> = try {
            withContext(ioDispatcher) {
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    buildList<Download> { while (cursor.moveToNext()) add(cursor.download) }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Could not read the download index", e)
            return DataError.Local.UNKNOWN
        }
        stored.forEach { requests[it.request.id] = it.request }
        val loaded = stored.associate { it.request.id to it.toSnapshot() }.toMutableMap()
        pendingChanges.forEach { (id, download) ->
            if (download == null) loaded -= id else loaded[id] = download.toSnapshot()
        }
        pendingChanges.clear()
        snapshots.value = loaded
        updateProgressPolling()
        null
    }

    private fun apply(id: String, download: Download?) {
        val current = snapshots.value
        if (current == null) {
            pendingChanges[id] = download
        } else {
            snapshots.value = if (download == null) current - id else current + (id to download.toSnapshot())
        }
        if (download == null) requests -= id else requests[id] = download.request
        updateProgressPolling()
    }

    private fun updateProgressPolling() {
        val downloading = snapshots.value.orEmpty().values.any { it.state == Download.STATE_DOWNLOADING }
        if (downloading && progressPolling?.isActive != true) {
            progressPolling = scope.launch {
                while (isActive) {
                    delay(PROGRESS_INTERVAL)
                    downloadManager.currentDownloads.forEach { apply(it.request.id, it) }
                }
            }
        } else if (!downloading) {
            progressPolling?.cancel()
            progressPolling = null
        }
    }

    private suspend fun DownloadHelper.prepareAndAwait() = suspendCancellableCoroutine { continuation ->
        prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                continuation.resume(Unit)
            }

            override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }

    private inner class ManagerListener : DownloadManager.Listener {
        override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
            apply(download.request.id, download)
        }

        override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
            apply(download.request.id, null)
        }

        override fun onRequirementsStateChanged(
            manager: DownloadManager,
            requirements: Requirements,
            notMetRequirements: Int,
        ) {
            this@MediaDownloads.notMetRequirements.value = notMetRequirements
        }
    }

    private companion object {
        const val TAG = "StreamlyDownloads"
        const val DOWNLOAD_THREADS = 4
        const val MAX_VIDEO_WIDTH = 854
        const val MAX_VIDEO_HEIGHT = 480
        val PROGRESS_INTERVAL = 500.milliseconds
    }
}
