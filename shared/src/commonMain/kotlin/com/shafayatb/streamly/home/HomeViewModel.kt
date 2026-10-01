package com.shafayatb.streamly.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.core.presentation.toUiText
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.video.Video
import com.shafayatb.streamly.domain.video.VideoRepository
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val videoRepository: VideoRepository,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** `null` while loading. */
    private val feed = MutableStateFlow<Result<List<Video>, DataError.Network>?>(null)

    // Saved so the chosen chip survives process death, not just configuration changes.
    private val selectedFilter = savedStateHandle.getStateFlow(KEY_FILTER, FeedFilter.ALL.name)
        .map { name -> FeedFilter.entries.firstOrNull { it.name == name } ?: FeedFilter.ALL }

    val state: StateFlow<HomeState> = combine(feed, selectedFilter) { feed, filter ->
        HomeState(selectedFilter = filter, feed = feed.toContent(filter))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState())

    private val _events = Channel<HomeEvent>()
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.SelectFilter -> savedStateHandle[KEY_FILTER] = intent.filter.name
            is HomeIntent.OpenVideo -> viewModelScope.launch {
                _events.send(HomeEvent.NavigateToPlayer(intent.videoId))
            }
            HomeIntent.Retry -> load()
        }
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        feed.value = null
        loadJob = viewModelScope.launch {
            feed.value = videoRepository.getFeed()
        }
    }

    private fun Result<List<Video>, DataError.Network>?.toContent(filter: FeedFilter): FeedContent =
        when (this) {
            null -> FeedContent.Loading
            is Result.Failure -> FeedContent.Error(error.toUiText())
            is Result.Success -> {
                val now = clock.now()
                val cards = data.filter(filter::matches).map { it.toCardUi(now) }
                if (cards.isEmpty()) FeedContent.Empty else FeedContent.Loaded(cards.toImmutableList())
            }
        }

    private companion object {
        const val KEY_FILTER = "selected_filter"
    }
}
