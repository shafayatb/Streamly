package com.shafayatb.streamly.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.formatAge
import com.shafayatb.streamly.core.presentation.formatDuration
import com.shafayatb.streamly.core.presentation.formatViewCount
import com.shafayatb.streamly.core.presentation.toUiText
import com.shafayatb.streamly.domain.player.PlaybackState
import com.shafayatb.streamly.domain.player.PlaybackStatus
import com.shafayatb.streamly.domain.player.VideoPlayer
import com.shafayatb.streamly.domain.util.onFailure
import com.shafayatb.streamly.domain.util.onSuccess
import com.shafayatb.streamly.domain.video.Video
import com.shafayatb.streamly.domain.video.VideoRepository
import com.shafayatb.streamly.home.toCardUi
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.player_share_unavailable

/**
 * Plays [videoId] on the app's shared [VideoPlayer] and loads its details and up next list.
 *
 * The player outlives this screen, so the ViewModel only controls it while it still has
 * [videoId] loaded. A screen being replaced (for example by an up-next pick) therefore cannot
 * pause or stop the video that replaced it.
 */
class PlayerViewModel(
    private val videoId: String,
    private val videoRepository: VideoRepository,
    private val videoPlayer: VideoPlayer,
    private val clock: Clock,
) : ViewModel() {

    private val screen = MutableStateFlow(PlayerState())

    val state: StateFlow<PlayerState> = combine(screen, videoPlayer.state) { screen, playback ->
        val isLiveVideo = (screen.content as? PlayerContent.Loaded)?.video?.isLive == true
        screen.copy(playback = playback.toPlaybackUi(videoId, isLiveVideo))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, screen.value)

    private val _events = Channel<PlayerEvent>()
    val events: Flow<PlayerEvent> = _events.receiveAsFlow()

    private var detailsJob: Job? = null
    private var upNextJob: Job? = null

    // Assume visible until told otherwise: the screen is being composed as this is created.
    private var isScreenVisible = true

    // Set when the screen hid while playing, so playback resumes only if it was playing.
    private var resumeWhenShown = false

    private val ownsPlayer: Boolean get() = videoPlayer.state.value.videoId == videoId

    init {
        loadDetails()
        loadUpNext()
    }

    fun onIntent(intent: PlayerIntent) {
        when (intent) {
            PlayerIntent.TogglePlayPause -> togglePlayPause()
            is PlayerIntent.SeekTo -> if (ownsPlayer) videoPlayer.seekTo(intent.position)
            PlayerIntent.ToggleMute -> videoPlayer.setMuted(!videoPlayer.state.value.isMuted)
            PlayerIntent.RetryDetails -> loadDetails()
            PlayerIntent.RetryPlayback -> if (ownsPlayer) videoPlayer.retry()
            PlayerIntent.RetryUpNext -> loadUpNext()
            is PlayerIntent.SelectUpNext -> selectUpNext(intent.videoId)
            PlayerIntent.NavigateBack -> send(PlayerEvent.NavigateBack)
            PlayerIntent.ToggleLike -> screen.update { it.copy(isLiked = !it.isLiked) }
            PlayerIntent.ToggleSubscribe -> screen.update { it.copy(isSubscribed = !it.isSubscribed) }
            PlayerIntent.Share -> send(PlayerEvent.ShowMessage(UiText.Resource(Res.string.player_share_unavailable)))
            PlayerIntent.ScreenShown -> onScreenShown()
            PlayerIntent.ScreenHidden -> onScreenHidden()
        }
    }

    override fun onCleared() {
        // The screen is gone for good (Back, or replaced). The player stays for the next screen.
        if (ownsPlayer) videoPlayer.stop()
    }

    private fun togglePlayPause() {
        if (!ownsPlayer) return
        if (videoPlayer.state.value.showsPause) videoPlayer.pause() else videoPlayer.play()
    }

    private fun selectUpNext(nextVideoId: String) {
        // Silence this video now rather than when the replaced screen finishes leaving.
        if (ownsPlayer) videoPlayer.stop()
        send(PlayerEvent.NavigateToVideo(nextVideoId))
    }

    private fun onScreenShown() {
        isScreenVisible = true
        if (resumeWhenShown && ownsPlayer) videoPlayer.play()
        resumeWhenShown = false
    }

    private fun onScreenHidden() {
        isScreenVisible = false
        val playback = videoPlayer.state.value
        // An ended video keeps playWhenReady; resuming it would restart it from the beginning.
        if (ownsPlayer && playback.playWhenReady && !playback.isEnded) {
            videoPlayer.pause()
            resumeWhenShown = true
        }
    }

    private fun startPlayback(video: Video) {
        if (ownsPlayer) return
        // A video that finishes loading while the app is in the background waits to be seen.
        videoPlayer.load(video, playWhenReady = isScreenVisible)
        resumeWhenShown = !isScreenVisible
    }

    private fun loadDetails() {
        if (detailsJob?.isActive == true) return
        screen.update { it.copy(content = PlayerContent.Loading) }
        detailsJob = viewModelScope.launch {
            videoRepository.getVideo(videoId)
                .onSuccess { video ->
                    screen.update { it.copy(content = PlayerContent.Loaded(video.toDetailsUi(clock.now()))) }
                    startPlayback(video)
                }
                .onFailure { error -> screen.update { it.copy(content = PlayerContent.Error(error.toUiText())) } }
        }
    }

    private fun loadUpNext() {
        if (upNextJob?.isActive == true) return
        screen.update { it.copy(upNext = UpNextContent.Loading) }
        upNextJob = viewModelScope.launch {
            videoRepository.getUpNext(videoId)
                .onSuccess { videos ->
                    val now = clock.now()
                    val cards = videos.map { it.toCardUi(now) }.toImmutableList()
                    screen.update { it.copy(upNext = UpNextContent.Loaded(cards)) }
                }
                .onFailure { error -> screen.update { it.copy(upNext = UpNextContent.Error(error.toUiText())) } }
        }
    }

    private fun send(event: PlayerEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}

internal fun Video.toDetailsUi(now: Instant): VideoDetailsUi = VideoDetailsUi(
    id = id,
    title = title,
    channelName = channel.name,
    description = description,
    thumbnailUrl = thumbnailUrl,
    views = formatViewCount(viewCount),
    age = formatAge(publishedAt, now),
    isLive = isLive,
)

/**
 * Projects the shared player's state onto the screen showing [videoId]. While the player holds
 * another video (or none), the screen shows idle controls; mute is global, so it always shows.
 */
internal fun PlaybackState.toPlaybackUi(videoId: String, isLiveVideo: Boolean): PlaybackUi {
    if (this.videoId != videoId) return PlaybackUi(isLive = isLiveVideo, isMuted = isMuted)
    val live = isLive || isLiveVideo
    return PlaybackUi(
        isPlaying = showsPause,
        isBuffering = isBuffering,
        isEnded = isEnded,
        isLive = live,
        isMuted = isMuted,
        position = position,
        duration = duration.takeUnless { live },
        positionText = formatDuration(position),
        durationText = duration.takeUnless { live }?.let(::formatDuration),
        error = error?.toUiText(),
    )
}

// Pause shows while playback is requested and can still happen, as in Media3's own controls.
private val PlaybackState.showsPause: Boolean
    get() = playWhenReady && status != PlaybackStatus.IDLE && status != PlaybackStatus.ENDED
