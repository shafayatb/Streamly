package com.shafayatb.streamly.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.formatAge
import com.shafayatb.streamly.core.presentation.formatDuration
import com.shafayatb.streamly.domain.history.WatchHistoryEntry
import com.shafayatb.streamly.domain.history.WatchHistoryRepository
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.util.onFailure
import kotlin.time.Clock
import kotlin.time.Instant
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
import streamly.shared.generated.resources.history_error_body
import streamly.shared.generated.resources.history_update_failed

class WatchHistoryViewModel(
    private val watchHistory: WatchHistoryRepository,
    private val clock: Clock,
) : ViewModel() {

    /** `null` while loading. */
    private val history = MutableStateFlow<Result<List<WatchHistoryEntry>, DataError.Local>?>(null)
    private val isClearDialogRequested = MutableStateFlow(false)

    val state: StateFlow<WatchHistoryState> = combine(history, isClearDialogRequested) { history, clearRequested ->
        val content = when (history) {
            null -> WatchHistoryContent.Loading
            is Result.Failure -> WatchHistoryContent.Error(UiText.Resource(Res.string.history_error_body))
            is Result.Success -> if (history.data.isEmpty()) {
                WatchHistoryContent.Empty
            } else {
                val now = clock.now()
                WatchHistoryContent.Loaded(history.data.map { it.toItemUi(now) }.toImmutableList())
            }
        }
        // A history that empties under the dialog closes it rather than offering to clear nothing.
        WatchHistoryState(content = content, isClearDialogShown = clearRequested && content is WatchHistoryContent.Loaded)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WatchHistoryState())

    private val _events = Channel<WatchHistoryEvent>()
    val events: Flow<WatchHistoryEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onIntent(intent: WatchHistoryIntent) {
        when (intent) {
            is WatchHistoryIntent.Open -> send(WatchHistoryEvent.NavigateToPlayer(intent.videoId))
            is WatchHistoryIntent.Remove -> update { watchHistory.remove(intent.videoId) }
            WatchHistoryIntent.RequestClear -> isClearDialogRequested.value = true
            WatchHistoryIntent.ConfirmClear -> {
                isClearDialogRequested.value = false
                update { watchHistory.clear() }
            }
            WatchHistoryIntent.DismissClear -> isClearDialogRequested.value = false
            WatchHistoryIntent.RetryLoad -> load()
            WatchHistoryIntent.NavigateBack -> send(WatchHistoryEvent.NavigateBack)
        }
    }

    private fun load() {
        loadJob?.cancel()
        history.value = null
        loadJob = viewModelScope.launch {
            watchHistory.entries.collect { history.value = it }
        }
    }

    private fun update(write: suspend () -> EmptyResult<DataError.Local>) {
        viewModelScope.launch {
            write().onFailure { _events.send(WatchHistoryEvent.ShowMessage(UiText.Resource(Res.string.history_update_failed))) }
        }
    }

    private fun send(event: WatchHistoryEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}

private fun WatchHistoryEntry.toItemUi(now: Instant): HistoryItemUi = HistoryItemUi(
    videoId = videoId,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    durationText = duration?.let(::formatDuration),
    isLive = isLive,
    progress = progress,
    watched = formatAge(watchedAt, now),
)
