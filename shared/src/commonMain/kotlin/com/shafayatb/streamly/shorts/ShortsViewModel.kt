package com.shafayatb.streamly.shorts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.abbreviateCount
import com.shafayatb.streamly.core.presentation.toUiText
import com.shafayatb.streamly.domain.player.PlaybackStatus
import com.shafayatb.streamly.domain.player.ShortsPlayerPool
import com.shafayatb.streamly.domain.player.ShortsPoolState
import com.shafayatb.streamly.domain.shorts.ShortVideo
import com.shafayatb.streamly.domain.shorts.ShortsRepository
import com.shafayatb.streamly.domain.util.onFailure
import com.shafayatb.streamly.domain.util.onSuccess
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.player_share_unavailable
import streamly.shared.generated.resources.shorts_comments_unavailable

/**
 * Loads the shorts and drives the [ShortsPlayerPool] from the settled page: the visible short
 * plays and the next one is prepared. The screen leases the pool for its whole life and closes
 * the lease when it is cleared, which releases the players.
 */
class ShortsViewModel(
    private val shortsRepository: ShortsRepository,
    playerPool: ShortsPlayerPool,
) : ViewModel() {

    private val players = playerPool.acquire()

    private val screen = MutableStateFlow(ShortsState())

    val state: StateFlow<ShortsState> = combine(screen, players.state) { screen, pool ->
        screen.copy(
            playback = pool.toPlaybackUi(currentShortId(screen)),
            isMuted = pool.isMuted,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, screen.value)

    private val _events = Channel<ShortsEvent>()
    val events: Flow<ShortsEvent> = _events.receiveAsFlow()

    private var shorts: List<ShortVideo> = emptyList()
    private val likedIds = mutableSetOf<String>()
    private var loadJob: Job? = null

    // Assume visible until told otherwise: the screen is being composed as this is created.
    private var isScreenVisible = true

    // Set when the screen hid while playing, so playback resumes only if it was playing.
    private var resumeWhenShown = false

    init {
        load()
    }

    fun onIntent(intent: ShortsIntent) {
        when (intent) {
            is ShortsIntent.PageSettled -> onPageSettled(intent.page)
            ShortsIntent.TogglePlayPause -> togglePlayPause()
            ShortsIntent.ToggleMute -> players.setMuted(!players.state.value.isMuted)
            is ShortsIntent.ToggleLike -> toggleLike(intent.shortId)
            is ShortsIntent.Comment -> send(ShortsEvent.ShowMessage(UiText.Resource(Res.string.shorts_comments_unavailable)))
            is ShortsIntent.Share -> send(ShortsEvent.ShowMessage(UiText.Resource(Res.string.player_share_unavailable)))
            ShortsIntent.Retry -> load()
            ShortsIntent.RetryPlayback -> players.retry()
            ShortsIntent.ScreenShown -> onScreenShown()
            ShortsIntent.ScreenHidden -> onScreenHidden()
        }
    }

    override fun onCleared() {
        // Shorts is gone for good (Back, or another tab), so its players are released.
        players.close()
    }

    private fun onPageSettled(page: Int) {
        val visible = shorts.getOrNull(page) ?: return
        // A rotation or recomposition re-reports the page that is already playing.
        if (page == screen.value.currentPage && players.state.value.visibleId == visible.id) return
        screen.update { it.copy(currentPage = page) }
        players.show(visible, upcoming = shorts.getOrNull(page + 1), playWhenReady = isScreenVisible)
        resumeWhenShown = !isScreenVisible
    }

    private fun togglePlayPause() {
        val visible = players.state.value.visible ?: return
        // A failed short offers its own retry; a stray tap must not leave it paused once it recovers.
        if (visible.error != null) return
        if (visible.playWhenReady) players.pause() else players.play()
    }

    private fun onScreenShown() {
        isScreenVisible = true
        if (resumeWhenShown) players.play()
        resumeWhenShown = false
    }

    private fun onScreenHidden() {
        isScreenVisible = false
        if (players.state.value.visible?.playWhenReady == true) {
            players.pause()
            resumeWhenShown = true
        }
    }

    private fun toggleLike(shortId: String) {
        if (!likedIds.remove(shortId)) likedIds += shortId
        publishShorts()
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        screen.update { it.copy(content = ShortsContent.Loading) }
        loadJob = viewModelScope.launch {
            shortsRepository.getShorts()
                .onSuccess { loaded ->
                    shorts = loaded
                    publishShorts()
                }
                .onFailure { error -> screen.update { it.copy(content = ShortsContent.Error(error.toUiText())) } }
        }
    }

    private fun publishShorts() {
        val content = if (shorts.isEmpty()) {
            ShortsContent.Empty
        } else {
            ShortsContent.Loaded(shorts.map { it.toUi(isLiked = it.id in likedIds) }.toImmutableList())
        }
        screen.update { it.copy(content = content) }
    }

    private fun currentShortId(screen: ShortsState): String? =
        (screen.content as? ShortsContent.Loaded)?.shorts?.getOrNull(screen.currentPage)?.id

    private fun send(event: ShortsEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}

internal fun ShortVideo.toUi(isLiked: Boolean): ShortUi = ShortUi(
    id = id,
    title = title,
    channelName = channel.name,
    channelHandle = channel.id,
    likes = abbreviateCount(likeCount + if (isLiked) 1 else 0),
    comments = abbreviateCount(commentCount),
    isLiked = isLiked,
)

/** The current short's playback, or idle defaults while it holds no player. */
internal fun ShortsPoolState.toPlaybackUi(shortId: String?): ShortPlaybackUi {
    val playback = shortId?.let(players::get) ?: return ShortPlaybackUi()
    return ShortPlaybackUi(
        isBuffering = playback.isBuffering && playback.playWhenReady,
        isPaused = !playback.playWhenReady && playback.error == null && playback.status != PlaybackStatus.IDLE,
        error = playback.error?.toUiText(),
    )
}
