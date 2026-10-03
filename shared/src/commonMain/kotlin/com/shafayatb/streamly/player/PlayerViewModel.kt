package com.shafayatb.streamly.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.core.presentation.formatAge
import com.shafayatb.streamly.core.presentation.formatDuration
import com.shafayatb.streamly.core.presentation.formatViewCount
import com.shafayatb.streamly.core.presentation.toUiText
import com.shafayatb.streamly.domain.download.DownloadRepository
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.VideoDownload
import com.shafayatb.streamly.domain.history.WatchHistoryRepository
import com.shafayatb.streamly.domain.player.PlaybackState
import com.shafayatb.streamly.domain.player.PlaybackStatus
import com.shafayatb.streamly.domain.player.VideoPlayer
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.domain.util.onFailure
import com.shafayatb.streamly.domain.util.onSuccess
import com.shafayatb.streamly.domain.video.Video
import com.shafayatb.streamly.domain.video.VideoRepository
import com.shafayatb.streamly.home.toCardUi
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
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
import streamly.shared.generated.resources.download_remove_failed
import streamly.shared.generated.resources.player_share_unavailable

/**
 * Plays [videoId] on the app's shared [VideoPlayer] and loads its details and up next list.
 *
 * The player outlives this screen, so the ViewModel only controls it while it still has
 * [videoId] loaded. A screen being replaced (for example by an up-next pick) therefore cannot
 * pause or stop the video that replaced it. It also resumes the video from the account's watch
 * history and records how far it got.
 */
class PlayerViewModel(
    private val videoId: String,
    private val videoRepository: VideoRepository,
    private val videoPlayer: VideoPlayer,
    private val downloadRepository: DownloadRepository,
    private val watchHistory: WatchHistoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val screen = MutableStateFlow(PlayerState())

    private val downloadEntry = MutableStateFlow<VideoDownload?>(null)

    // Covers the gap between a tap on Download and the downloader reporting the new download.
    private val isStartingDownload = MutableStateFlow(false)

    val state: StateFlow<PlayerState> = combine(
        screen,
        videoPlayer.state,
        downloadEntry,
        isStartingDownload,
    ) { screen, playback, download, isStarting ->
        val isLive = (screen.content as? PlayerContent.Loaded)?.video?.isLive
        screen.copy(
            playback = playback.toPlaybackUi(videoId, isLiveVideo = isLive == true),
            download = downloadActionOf(isLive, download, isStarting),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, screen.value)

    private val _events = Channel<PlayerEvent>()
    val events: Flow<PlayerEvent> = _events.receiveAsFlow()

    private var detailsJob: Job? = null
    private var loadedVideo: Video? = null
    private var downloadStart: Job? = null
    private var upNextJob: Job? = null

    // Assume visible until told otherwise: the screen is being composed as this is created.
    private var isScreenVisible = true

    // Set when the screen hid while playing, so playback resumes only if it was playing.
    private var resumeWhenShown = false

    // Set once this screen's video has actually played: only then does it belong in the history.
    private var hasPlayed = false
    private var lastRecordedPosition: Duration? = null
    private var wasPlayWhenReady = false

    private val ownsPlayer: Boolean get() = videoPlayer.state.value.videoId == videoId

    init {
        viewModelScope.launch { videoPlayer.state.collect(::trackHistory) }
        loadDetails()
        loadUpNext()
        viewModelScope.launch {
            downloadRepository.downloads.collect { result ->
                val download = (result as? Result.Success)?.data?.firstOrNull { it.videoId == videoId }
                downloadEntry.value = download
                if (download != null) isStartingDownload.value = false
                // Only a finished download can be removed from here; close the dialog otherwise.
                if (download?.status != DownloadStatus.COMPLETED) {
                    screen.update { it.copy(isRemoveDownloadDialogShown = false) }
                }
            }
        }
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
            PlayerIntent.NavigateBack -> if (screen.value.fullscreen.isFullscreen) {
                updateFullscreen(FullscreenInput.Exit)
            } else {
                send(PlayerEvent.NavigateBack)
            }
            PlayerIntent.EnterFullscreen -> updateFullscreen(FullscreenInput.Enter)
            PlayerIntent.ExitFullscreen -> updateFullscreen(FullscreenInput.Exit)
            is PlayerIntent.WindowChanged -> updateFullscreen(FullscreenInput.WindowChanged(intent.shape))
            is PlayerIntent.DeviceOrientationChanged ->
                updateFullscreen(FullscreenInput.DeviceChanged(intent.orientation, intent.autoRotate))
            PlayerIntent.ToggleLike -> screen.update { it.copy(isLiked = !it.isLiked) }
            PlayerIntent.ToggleSubscribe -> screen.update { it.copy(isSubscribed = !it.isSubscribed) }
            PlayerIntent.Share -> send(PlayerEvent.ShowMessage(UiText.Resource(Res.string.player_share_unavailable)))
            PlayerIntent.ScreenShown -> onScreenShown()
            PlayerIntent.ScreenHidden -> onScreenHidden()
            PlayerIntent.Download -> startDownload()
            PlayerIntent.CancelDownload -> cancelDownload()
            PlayerIntent.RequestRemoveDownload -> if (downloadEntry.value?.status == DownloadStatus.COMPLETED) {
                screen.update { it.copy(isRemoveDownloadDialogShown = true) }
            }
            PlayerIntent.ConfirmRemoveDownload -> {
                screen.update { it.copy(isRemoveDownloadDialogShown = false) }
                removeDownload()
            }
            PlayerIntent.DismissRemoveDownload -> screen.update { it.copy(isRemoveDownloadDialogShown = false) }
        }
    }

    override fun onCleared() {
        // The screen is gone for good (Back, or replaced). The player stays for the next screen.
        // Saved first: stopping unloads the video and its position.
        recordProgress()
        if (ownsPlayer) videoPlayer.stop()
    }

    private fun updateFullscreen(input: FullscreenInput) {
        screen.update { it.copy(fullscreen = it.fullscreen.reduce(input)) }
    }

    private fun togglePlayPause() {
        if (!ownsPlayer) return
        if (videoPlayer.state.value.showsPause) videoPlayer.pause() else videoPlayer.play()
    }

    private fun selectUpNext(nextVideoId: String) {
        recordProgress()
        // Silence this video now rather than when the replaced screen finishes leaving.
        if (ownsPlayer) videoPlayer.stop()
        send(PlayerEvent.NavigateToVideo(nextVideoId))
    }

    private fun startDownload() {
        if (downloadEntry.value?.status == DownloadStatus.FAILED) {
            downloadRepository.retry(videoId)
            return
        }
        val video = loadedVideo ?: return
        if (isStartingDownload.value || downloadEntry.value != null) return
        isStartingDownload.value = true
        downloadStart = viewModelScope.launch {
            downloadRepository.download(video).onFailure { error ->
                isStartingDownload.value = false
                send(PlayerEvent.ShowMessage(error.toUiText()))
            }
        }
    }

    private fun cancelDownload() {
        // Also covers a download still being prepared: the repository drops that start too.
        downloadStart?.cancel()
        isStartingDownload.value = false
        removeDownload()
    }

    private fun removeDownload() {
        viewModelScope.launch {
            downloadRepository.remove(videoId).onFailure {
                send(PlayerEvent.ShowMessage(UiText.Resource(Res.string.download_remove_failed)))
            }
        }
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
        recordProgress()
    }

    private suspend fun startPlayback(video: Video) {
        if (ownsPlayer) return
        val start = if (video.isLive) Duration.ZERO else watchHistory.resumePosition(video.id)
        // A video that finishes loading while the app is in the background waits to be seen.
        videoPlayer.load(video, playWhenReady = isScreenVisible, startPosition = start)
        resumeWhenShown = !isScreenVisible
    }

    /** Records the moments history needs: the first play, a pause, the end, and a checkpoint while playing. */
    private fun trackHistory(playback: PlaybackState) {
        if (playback.videoId != videoId) return
        val paused = wasPlayWhenReady && !playback.playWhenReady
        wasPlayWhenReady = playback.playWhenReady
        when {
            !hasPlayed -> if (playback.isPlaying) {
                hasPlayed = true
                recordProgress(playback, isFirstPlay = true)
            }
            paused || playback.isEnded -> recordProgress(playback)
            playback.isPlaying && isCheckpointDue(playback.position) -> recordProgress(playback)
        }
    }

    private fun isCheckpointDue(position: Duration): Boolean {
        val last = lastRecordedPosition ?: return true
        return (position - last).absoluteValue >= HISTORY_CHECKPOINT
    }

    /**
     * Saves where this screen's video is, once it has played and only if the position moved. Only the
     * first play adds the video; these saves update it, so one the user removed stays removed.
     */
    private fun recordProgress(playback: PlaybackState = videoPlayer.state.value, isFirstPlay: Boolean = false) {
        val video = loadedVideo ?: return
        if (!hasPlayed || playback.videoId != videoId) return
        val position = if (video.isLive) Duration.ZERO else playback.position
        if (position == lastRecordedPosition) return
        lastRecordedPosition = position
        if (isFirstPlay) {
            watchHistory.record(video, position, playback.duration)
        } else {
            watchHistory.updateProgress(video, position, playback.duration)
        }
    }

    private fun loadDetails() {
        if (detailsJob?.isActive == true) return
        screen.update { it.copy(content = PlayerContent.Loading) }
        detailsJob = viewModelScope.launch {
            videoRepository.getVideo(videoId)
                .onSuccess { video ->
                    loadedVideo = video
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

// How often a playing video's position is saved, so a crash or process death loses little.
private val HISTORY_CHECKPOINT = 10.seconds

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
