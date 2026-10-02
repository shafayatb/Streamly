package com.shafayatb.streamly.player

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.test
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.player.PlaybackError
import com.shafayatb.streamly.domain.player.PlaybackStatus
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.home.toCardUi
import com.shafayatb.streamly.testing.FakeVideoPlayer
import com.shafayatb.streamly.testing.FakeVideoRepository
import com.shafayatb.streamly.testing.testVideo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.error_network_no_internet
import streamly.shared.generated.resources.error_network_not_found
import streamly.shared.generated.resources.player_error_network
import streamly.shared.generated.resources.player_share_unavailable

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val now = Instant.parse("2026-10-01T00:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private val video = testVideo(id = "v1", title = "Media3 in 10 minutes", channelName = "CodeLabs", duration = 10.minutes)
    private val next = testVideo(id = "v2")
    private val live = testVideo(id = "live", duration = null)
    private val repository = FakeVideoRepository(videos = listOf(video, next, live))
    private val player = FakeVideoPlayer()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(videoId: String = "v1") = PlayerViewModel(videoId, repository, player, clock)

    @Test
    fun showsLoadingUntilTheVideoArrives() {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val viewModel = viewModel()

        assertEquals(PlayerContent.Loading, viewModel.state.value.content)
        assertEquals(UpNextContent.Loading, viewModel.state.value.upNext)
        assertTrue(player.loadedVideoIds.isEmpty())

        gate.complete(Unit)
        assertEquals(PlayerContent.Loaded(video.toDetailsUi(now)), viewModel.state.value.content)
    }

    @Test
    fun loadedVideoStartsPlayingWithUpNextBelow() {
        val viewModel = viewModel()

        val state = viewModel.state.value
        val details = (state.content as PlayerContent.Loaded).video
        assertEquals("Media3 in 10 minutes", details.title)
        assertEquals("CodeLabs", details.channelName)
        assertEquals(UpNextContent.Loaded(listOf(next, live).map { it.toCardUi(now) }.toImmutableList()), state.upNext)
        assertEquals(listOf("v1"), player.loadedVideoIds)
        assertTrue(state.playback.isPlaying)
        assertEquals("0:00", state.playback.positionText)
        assertEquals("10:00", state.playback.durationText)
    }

    @Test
    fun unknownVideoShowsNotFoundAndPlaysNothing() {
        val viewModel = viewModel(videoId = "missing")

        assertEquals(
            PlayerContent.Error(UiText.Resource(Res.string.error_network_not_found)),
            viewModel.state.value.content,
        )
        assertTrue(player.loadedVideoIds.isEmpty())
    }

    @Test
    fun retryReloadsTheDetailsAndStartsPlayback() {
        repository.failure = DataError.Network.NO_INTERNET
        val viewModel = viewModel()
        assertEquals(
            PlayerContent.Error(UiText.Resource(Res.string.error_network_no_internet)),
            viewModel.state.value.content,
        )

        repository.failure = null
        viewModel.onIntent(PlayerIntent.RetryDetails)

        assertEquals(PlayerContent.Loaded(video.toDetailsUi(now)), viewModel.state.value.content)
        assertEquals(listOf("v1"), player.loadedVideoIds)
    }

    @Test
    fun upNextFailsOnItsOwnAndRetries() {
        repository.upNextFailure = DataError.Network.NO_INTERNET
        val viewModel = viewModel()
        assertIs<PlayerContent.Loaded>(viewModel.state.value.content)
        assertEquals(
            UpNextContent.Error(UiText.Resource(Res.string.error_network_no_internet)),
            viewModel.state.value.upNext,
        )

        repository.upNextFailure = null
        viewModel.onIntent(PlayerIntent.RetryUpNext)

        assertIs<UpNextContent.Loaded>(viewModel.state.value.upNext)
    }

    @Test
    fun playPauseTogglesPlayback() {
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.TogglePlayPause)
        assertFalse(viewModel.state.value.playback.isPlaying)

        viewModel.onIntent(PlayerIntent.TogglePlayPause)
        assertTrue(viewModel.state.value.playback.isPlaying)
    }

    @Test
    fun playingAnEndedVideoStartsItAgain() {
        val viewModel = viewModel()
        player.report { it.copy(status = PlaybackStatus.ENDED, position = 10.minutes) }
        assertTrue(viewModel.state.value.playback.isEnded)
        assertFalse(viewModel.state.value.playback.isPlaying)

        viewModel.onIntent(PlayerIntent.TogglePlayPause)

        assertTrue(viewModel.state.value.playback.isPlaying)
        assertEquals("0:00", viewModel.state.value.playback.positionText)
    }

    @Test
    fun seekMovesThePlayerAndTheElapsedTime() {
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.SeekTo(2.minutes + 5.seconds))

        assertEquals(2.minutes + 5.seconds, player.state.value.position)
        assertEquals("2:05", viewModel.state.value.playback.positionText)
    }

    @Test
    fun muteToggles() {
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.ToggleMute)
        assertTrue(viewModel.state.value.playback.isMuted)

        viewModel.onIntent(PlayerIntent.ToggleMute)
        assertFalse(viewModel.state.value.playback.isMuted)
    }

    @Test
    fun bufferingIsShown() {
        val viewModel = viewModel()

        player.report { it.copy(status = PlaybackStatus.BUFFERING) }

        assertTrue(viewModel.state.value.playback.isBuffering)
        assertTrue(viewModel.state.value.playback.isPlaying, "still requested while buffering")
    }

    @Test
    fun playbackErrorShowsAMessageAndRetryPreparesAgain() {
        val viewModel = viewModel()
        player.report { it.copy(status = PlaybackStatus.IDLE, error = PlaybackError.NETWORK) }

        assertEquals(UiText.Resource(Res.string.player_error_network), viewModel.state.value.playback.error)

        viewModel.onIntent(PlayerIntent.RetryPlayback)

        assertEquals(1, player.retries)
        assertNull(viewModel.state.value.playback.error)
    }

    @Test
    fun liveStreamShowsLiveWithoutADuration() {
        val viewModel = viewModel(videoId = "live")

        val playback = viewModel.state.value.playback
        assertTrue(playback.isLive)
        assertNull(playback.duration)
        assertNull(playback.durationText)
        assertTrue((viewModel.state.value.content as PlayerContent.Loaded).video.isLive)
    }

    @Test
    fun selectingUpNextStopsThisVideoAndOpensTheNextOne() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(PlayerIntent.SelectUpNext("v2"))

            assertEquals(PlayerEvent.NavigateToVideo("v2"), awaitItem())
        }
        assertNull(player.state.value.videoId)
    }

    @Test
    fun hidingPausesAndShowingResumesWhatWasPlaying() {
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.ScreenHidden)
        assertFalse(player.state.value.playWhenReady)

        viewModel.onIntent(PlayerIntent.ScreenShown)
        assertTrue(player.state.value.playWhenReady)
    }

    @Test
    fun showingAgainKeepsAPausedVideoPaused() {
        val viewModel = viewModel()
        viewModel.onIntent(PlayerIntent.TogglePlayPause)

        viewModel.onIntent(PlayerIntent.ScreenHidden)
        viewModel.onIntent(PlayerIntent.ScreenShown)

        assertFalse(player.state.value.playWhenReady)
    }

    @Test
    fun aVideoThatLoadsWhileHiddenWaitsToBeShown() {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val viewModel = viewModel()
        viewModel.onIntent(PlayerIntent.ScreenHidden)

        gate.complete(Unit)
        assertEquals(listOf("v1"), player.loadedVideoIds)
        assertFalse(player.state.value.playWhenReady)

        viewModel.onIntent(PlayerIntent.ScreenShown)
        assertTrue(player.state.value.playWhenReady)
    }

    @Test
    fun aReplacedScreenCannotControlTheVideoThatReplacedIt() {
        val store = ViewModelStore()
        val stale = ViewModelProvider.create(store, viewModelFactory { initializer { viewModel("v1") } })
            .get(PlayerViewModel::class)
        val current = viewModel("v2")
        assertEquals("v2", player.state.value.videoId)

        stale.onIntent(PlayerIntent.ScreenHidden)
        stale.onIntent(PlayerIntent.TogglePlayPause)
        stale.onIntent(PlayerIntent.SeekTo(1.minutes))
        store.clear()

        assertEquals("v2", player.state.value.videoId)
        assertTrue(player.state.value.playWhenReady)
        assertEquals(0.seconds, player.state.value.position)
        assertFalse(stale.state.value.playback.isPlaying, "the stale screen shows idle controls")
        assertTrue(current.state.value.playback.isPlaying)
    }

    @Test
    fun leavingTheScreenStopsThePlayer() {
        val store = ViewModelStore()
        ViewModelProvider.create(store, viewModelFactory { initializer { viewModel() } })
            .get(PlayerViewModel::class)
        assertEquals("v1", player.state.value.videoId)

        store.clear()

        assertNull(player.state.value.videoId)
        assertFalse(player.state.value.playWhenReady)
    }

    @Test
    fun likeAndSubscribeToggle() {
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.ToggleLike)
        viewModel.onIntent(PlayerIntent.ToggleSubscribe)

        assertTrue(viewModel.state.value.isLiked)
        assertTrue(viewModel.state.value.isSubscribed)
    }

    @Test
    fun shareExplainsItIsNotAvailableYet() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(PlayerIntent.Share)

            assertEquals(PlayerEvent.ShowMessage(UiText.Resource(Res.string.player_share_unavailable)), awaitItem())
        }
    }

    @Test
    fun backSendsNavigateBack() = runTest {
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(PlayerIntent.NavigateBack)

            assertEquals(PlayerEvent.NavigateBack, awaitItem())
        }
    }
}
