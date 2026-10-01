package com.shafayatb.streamly.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.core.presentation.toUiText
import com.shafayatb.streamly.domain.util.onFailure
import com.shafayatb.streamly.domain.util.onSuccess
import com.shafayatb.streamly.domain.video.Video
import com.shafayatb.streamly.domain.video.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loads the details of [videoId]. Playback itself is not wired up yet. */
class PlayerViewModel(
    private val videoId: String,
    private val videoRepository: VideoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _events = Channel<PlayerEvent>()
    val events: Flow<PlayerEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.Retry -> load()
            PlayerIntent.NavigateBack -> viewModelScope.launch { _events.send(PlayerEvent.NavigateBack) }
        }
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        _state.update { it.copy(content = PlayerContent.Loading) }
        loadJob = viewModelScope.launch {
            videoRepository.getVideo(videoId)
                .onSuccess { video -> _state.update { it.copy(content = PlayerContent.Loaded(video.toDetailsUi())) } }
                .onFailure { error -> _state.update { it.copy(content = PlayerContent.Error(error.toUiText())) } }
        }
    }
}

internal fun Video.toDetailsUi(): VideoDetailsUi = VideoDetailsUi(
    id = id,
    title = title,
    channelName = channel.name,
    description = description,
    thumbnailUrl = thumbnailUrl,
)
