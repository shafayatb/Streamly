package com.shafayatb.streamly.domain.download

import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.session.accountKey
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.util.onFailure
import com.shafayatb.streamly.domain.util.onSuccess
import com.shafayatb.streamly.domain.video.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The signed-in account's downloads. A video's files are saved once on the device
 * ([DeviceDownloads]) and an ownership record lists the accounts that saved it, so two accounts
 * can share the files and removing a video for one keeps it for the other. Signing out hides an
 * account's downloads; signing in again as that account shows them.
 *
 * Call it from the main thread. [scope] must run there too, as [DeviceDownloads] requires, and
 * should live as long as the app: starts and removals finish in it after their screen is gone.
 */
public class AccountDownloadRepository(
    private val device: DeviceDownloads,
    private val ownership: DownloadOwnershipRepository,
    sessionRepository: SessionRepository,
    private val scope: CoroutineScope,
) : DownloadRepository, DownloadAccess {

    private val account: Flow<String?> = sessionRepository.session
        .map { it?.accountKey() }
        .distinctUntilChanged()

    /**
     * For [canPlayOffline], which must answer at once: `null` until the session and the owners
     * have been read, or once the owners could not be. Remove and retry read both afresh instead,
     * so a failed read cannot leave them doing nothing.
     */
    private val access: StateFlow<Access?> = combine(account, ownership.owners) { account, owners ->
        (owners as? Result.Success)?.let { Access(account, it.data) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    // This repository's own starts, so remove() can stop one before it queues a download nobody owns.
    private val starts = mutableMapOf<String, Job>()

    override val downloads: Flow<Result<List<VideoDownload>, DataError.Local>> =
        combine(device.downloads, account, ownership.owners) { downloads, account, owners ->
            ownedBy(account, downloads, owners)
        }.distinctUntilChanged()

    override val storage: Flow<StorageUsage> = combine(device.storage, downloads) { device, downloads ->
        StorageUsage(
            usedBytes = (downloads as? Result.Success)?.data.orEmpty()
                .filter { it.status != DownloadStatus.REMOVING }
                .sumOf { it.bytesDownloaded },
            freeBytes = device.freeBytes,
        )
    }.distinctUntilChanged()

    init {
        // Downloads saved before accounts owned them go to the first account that sees them.
        scope.launch {
            val (ids, account, owners) = combine(device.downloads, account, ownership.owners) { downloads, account, owners ->
                // A download still being removed lost its last owner on purpose.
                val ids = (downloads as? Result.Success)?.data
                    ?.filter { it.status != DownloadStatus.REMOVING }
                    ?.map { it.videoId }
                val known = (owners as? Result.Success)?.data
                if (ids == null || account == null || known == null) null else Triple(ids, account, known)
            }.filterNotNull().first()
            ids.filter { owners[it].isNullOrEmpty() }.forEach { ownership.addOwner(it, account) }
        }
    }

    override suspend fun download(video: Video): EmptyResult<DownloadError> {
        if (video.isLive) return Result.Failure(DownloadError.LIVE_NOT_SUPPORTED)
        val account = account.first() ?: return Result.Failure(DownloadError.UNKNOWN)
        // In this object's scope, like the device's own start: leaving the screen must not stop
        // between recording the owner and queuing the download.
        val start = scope.async { start(video, account) }
        starts[video.id] = start
        start.invokeOnCompletion { if (starts[video.id] === start) starts -= video.id }
        return start.await()
    }

    private suspend fun start(video: Video, account: String): EmptyResult<DownloadError> {
        if (ownership.addOwner(video.id, account) is Result.Failure) return Result.Failure(DownloadError.UNKNOWN)
        val existing = (device.downloads.first() as? Result.Success)?.data?.firstOrNull { it.videoId == video.id }
        return when (existing?.status) {
            DownloadStatus.QUEUED,
            DownloadStatus.WAITING_FOR_NETWORK,
            DownloadStatus.DOWNLOADING,
            DownloadStatus.COMPLETED,
            -> Result.Success(Unit)
            DownloadStatus.FAILED -> {
                device.retry(video.id)
                Result.Success(Unit)
            }
            DownloadStatus.REMOVING, null -> device.download(video).onFailure { ownership.removeOwner(video.id, account) }
        }
    }

    override fun retry(videoId: String) {
        scope.launch {
            val account = account.first() ?: return@launch
            val owners = (ownership.owners.first() as? Result.Success)?.data ?: return@launch
            if (Access(account, owners).owns(videoId)) device.retry(videoId)
        }
    }

    override fun remove(videoId: String) {
        starts.remove(videoId)?.cancel()
        scope.launch {
            val account = account.first() ?: return@launch
            ownership.removeOwner(videoId, account).onSuccess { remaining ->
                if (remaining.isEmpty()) device.remove(videoId)
            }
        }
    }

    // Until the owners are read, e.g. a player restored at launch, the saved copy is trusted: the
    // worst case is a non-owner watching the saved rendition of a video they opened anyway.
    override fun canPlayOffline(videoId: String): Boolean = access.value?.owns(videoId) ?: true

    private fun ownedBy(
        account: String?,
        downloads: Result<List<VideoDownload>, DataError.Local>,
        owners: Result<Map<String, Set<String>>, DataError.Local>,
    ): Result<List<VideoDownload>, DataError.Local> = when (downloads) {
        is Result.Failure -> downloads
        is Result.Success -> when (owners) {
            is Result.Failure -> owners
            is Result.Success -> Result.Success(downloads.data.filter { Access(account, owners.data).owns(it.videoId) })
        }
    }

    private data class Access(val account: String?, val owners: Map<String, Set<String>>) {
        fun owns(videoId: String): Boolean = account != null && account in owners[videoId].orEmpty()
    }
}
