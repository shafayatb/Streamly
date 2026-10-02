package com.shafayatb.streamly.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.formatBytes
import com.shafayatb.streamly.core.presentation.formatDuration
import com.shafayatb.streamly.core.presentation.toUiText
import com.shafayatb.streamly.domain.download.DownloadRepository
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.StorageUsage
import com.shafayatb.streamly.domain.download.VideoDownload
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.downloads_detail
import streamly.shared.generated.resources.downloads_progress
import streamly.shared.generated.resources.downloads_storage

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    /** `null` while loading. */
    private val downloads = MutableStateFlow<Result<List<VideoDownload>, DataError.Local>?>(null)
    private val storage = MutableStateFlow<StorageUsage?>(null)
    private val pendingRemovalId = MutableStateFlow<String?>(null)

    val state: StateFlow<DownloadsState> = combine(downloads, storage, pendingRemovalId) { downloads, storage, pendingId ->
        val items = (downloads as? Result.Success)?.data?.toItems()
        DownloadsState(
            content = when (downloads) {
                null -> DownloadsContent.Loading
                is Result.Failure -> DownloadsContent.Error(downloads.error.toUiText())
                is Result.Success -> if (items.isNullOrEmpty()) DownloadsContent.Empty else DownloadsContent.Loaded(items.toImmutableList())
            },
            storage = storage?.let {
                UiText.Resource(Res.string.downloads_storage, listOf(formatBytes(it.usedBytes), formatBytes(it.freeBytes)))
            },
            // A download that disappears closes its dialog rather than offering to remove nothing.
            pendingRemoval = items?.firstOrNull { it.videoId == pendingId },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DownloadsState())

    private val _events = Channel<DownloadsEvent>()
    val events: Flow<DownloadsEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch { downloadRepository.storage.collect { storage.value = it } }
    }

    fun onIntent(intent: DownloadsIntent) {
        when (intent) {
            is DownloadsIntent.Open -> open(intent.videoId)
            is DownloadsIntent.Cancel -> downloadRepository.remove(intent.videoId)
            is DownloadsIntent.Retry -> downloadRepository.retry(intent.videoId)
            is DownloadsIntent.RequestRemove -> pendingRemovalId.value = intent.videoId
            DownloadsIntent.ConfirmRemove -> {
                pendingRemovalId.value?.let(downloadRepository::remove)
                pendingRemovalId.value = null
            }
            DownloadsIntent.DismissRemove -> pendingRemovalId.value = null
            DownloadsIntent.RetryLoad -> load()
        }
    }

    private fun load() {
        loadJob?.cancel()
        downloads.value = null
        loadJob = viewModelScope.launch {
            downloadRepository.downloads.collect { downloads.value = it }
        }
    }

    private fun open(videoId: String) {
        val download = (downloads.value as? Result.Success)?.data?.firstOrNull { it.videoId == videoId }
        if (download?.status != DownloadStatus.COMPLETED) return
        viewModelScope.launch { _events.send(DownloadsEvent.NavigateToPlayer(videoId)) }
    }
}

/** Unfinished and failed downloads first, then finished ones; each group keeps the repository's newest-first order. */
private fun List<VideoDownload>.toItems(): List<DownloadItemUi> = filter { it.status != DownloadStatus.REMOVING }
    .sortedBy { it.status == DownloadStatus.COMPLETED }
    .map { it.toItemUi() }

private fun VideoDownload.toItemUi(): DownloadItemUi = DownloadItemUi(
    videoId = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    status = when (status) {
        DownloadStatus.QUEUED, DownloadStatus.REMOVING -> DownloadItemStatus.Queued
        DownloadStatus.WAITING_FOR_NETWORK -> DownloadItemStatus.WaitingForNetwork
        DownloadStatus.DOWNLOADING -> DownloadItemStatus.Downloading(
            progress = percent?.div(100f),
            text = UiText.Resource(
                Res.string.downloads_progress,
                listOf(percent?.let { "${it.toInt()}%" } ?: "…", formatBytes(bytesDownloaded)),
            ),
        )
        DownloadStatus.FAILED -> DownloadItemStatus.Failed
        DownloadStatus.COMPLETED -> DownloadItemStatus.Completed(
            UiText.Resource(Res.string.downloads_detail, listOf(formatBytes(bytesDownloaded), formatDuration(duration))),
        )
    },
)
