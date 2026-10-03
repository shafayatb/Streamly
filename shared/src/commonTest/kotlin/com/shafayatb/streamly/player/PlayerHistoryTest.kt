package com.shafayatb.streamly.player

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.shafayatb.streamly.domain.player.PlaybackStatus
import com.shafayatb.streamly.testing.FakeDownloadRepository
import com.shafayatb.streamly.testing.FakeVideoPlayer
import com.shafayatb.streamly.testing.FakeVideoRepository
import com.shafayatb.streamly.testing.FakeWatchHistoryRepository
import com.shafayatb.streamly.testing.FakeWatchHistoryRepository.Record
import com.shafayatb.streamly.testing.testVideo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerHistoryTest {

    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-03T00:00:00Z")
    }

    private val video = testVideo(id = "v1", duration = 10.minutes)
    private val next = testVideo(id = "v2", duration = 5.minutes)
    private val live = testVideo(id = "live", duration = null)
    private val repository = FakeVideoRepository(videos = listOf(video, next, live))
    private val player = FakeVideoPlayer()
    private val history = FakeWatchHistoryRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(videoId: String = "v1") = PlayerViewModel(
        videoId = videoId,
        videoRepository = repository,
        videoPlayer = player,
        downloadRepository = FakeDownloadRepository().apply { emit() },
        watchHistory = history,
        clock = clock,
    )

    private fun storedViewModel(store: ViewModelStore, videoId: String = "v1"): PlayerViewModel =
        ViewModelProvider.create(store, viewModelFactory { initializer { viewModel(videoId) } })
            .get(PlayerViewModel::class)

    private fun positions(videoId: String = "v1") = history.records.filter { it.videoId == videoId }.map { it.position }

    @Test
    fun resumesFromTheSavedPosition() {
        history.resumePositions["v1"] = 3.minutes

        val viewModel = viewModel()

        assertEquals(listOf(3.minutes), player.loadedStartPositions)
        assertEquals("3:00", viewModel.state.value.playback.positionText)
    }

    @Test
    fun startsFromTheBeginningWithoutHistory() {
        viewModel()

        assertEquals(listOf(Duration.ZERO), player.loadedStartPositions)
    }

    @Test
    fun waitsForTheSavedPositionBeforeLoading() {
        val gate = CompletableDeferred<Unit>()
        history.resumeGate = gate
        history.resumePositions["v1"] = 3.minutes

        viewModel()
        assertTrue(player.loadedVideoIds.isEmpty())

        gate.complete(Unit)
        assertEquals(listOf(3.minutes), player.loadedStartPositions)
    }

    @Test
    fun leavingBeforeTheSavedPositionArrivesLoadsNothing() {
        val gate = CompletableDeferred<Unit>()
        history.resumeGate = gate
        val store = ViewModelStore()
        storedViewModel(store)

        store.clear()
        gate.complete(Unit)

        assertTrue(player.loadedVideoIds.isEmpty())
        assertNull(player.state.value.videoId)
    }

    @Test
    fun aLiveVideoAlwaysStartsAtTheLiveEdge() {
        history.resumePositions["live"] = 3.minutes

        viewModel("live")

        assertEquals(listOf(Duration.ZERO), player.loadedStartPositions)
    }

    @Test
    fun recordsOnlyOncePlaybackStarts() {
        player.loadStatus = PlaybackStatus.BUFFERING
        viewModel()
        assertTrue(history.records.isEmpty())

        player.report { it.copy(status = PlaybackStatus.READY) }

        assertEquals(listOf(Record("v1", Duration.ZERO, 10.minutes)), history.records)
    }

    @Test
    fun aVideoThatNeverPlaysIsNotRecordedEvenOnLeaving() {
        player.loadStatus = PlaybackStatus.BUFFERING
        val store = ViewModelStore()
        val viewModel = storedViewModel(store)

        player.report { it.copy(status = PlaybackStatus.IDLE) }
        viewModel.onIntent(PlayerIntent.ScreenHidden)
        store.clear()

        assertTrue(history.records.isEmpty())
    }

    @Test
    fun checkpointsEveryTenSecondsOfPlayback() {
        viewModel()

        listOf(5.seconds, 10.seconds, 15.seconds, 20.seconds).forEach { position ->
            player.report { it.copy(position = position) }
        }

        assertEquals(listOf(Duration.ZERO, 10.seconds, 20.seconds), positions())
    }

    @Test
    fun onlyTheFirstSaveAddsTheVideo() {
        val viewModel = viewModel()
        player.report { it.copy(position = 10.seconds) }

        viewModel.onIntent(PlayerIntent.TogglePlayPause)
        viewModel.onIntent(PlayerIntent.SeekTo(30.seconds))
        viewModel.onIntent(PlayerIntent.SelectUpNext("v2"))

        // Later saves only update, so a video removed from the history meanwhile stays removed.
        assertEquals(listOf(true, false, false), history.records.map { it.inserts })
    }

    @Test
    fun savesOnPause() {
        val viewModel = viewModel()
        player.report { it.copy(position = 7.seconds) }

        viewModel.onIntent(PlayerIntent.TogglePlayPause)

        assertEquals(listOf(Duration.ZERO, 7.seconds), positions())
    }

    @Test
    fun savesWhenTheScreenIsHidden() {
        val viewModel = viewModel()
        player.report { it.copy(position = 4.seconds) }

        viewModel.onIntent(PlayerIntent.ScreenHidden)

        // The pause and the hide save the same position once.
        assertEquals(listOf(Duration.ZERO, 4.seconds), positions())
    }

    @Test
    fun savesTheFullLengthWhenTheVideoEnds() {
        viewModel()

        player.report { it.copy(status = PlaybackStatus.ENDED, position = 10.minutes) }

        assertEquals(10.minutes, positions().last())
    }

    @Test
    fun savesBeforeStoppingOnBack() {
        val store = ViewModelStore()
        val viewModel = storedViewModel(store)
        viewModel.onIntent(PlayerIntent.TogglePlayPause)
        viewModel.onIntent(PlayerIntent.SeekTo(2.minutes + 5.seconds))

        store.clear()

        assertEquals(2.minutes + 5.seconds, positions().last())
        assertNull(player.state.value.videoId)
    }

    @Test
    fun savesBeforeStoppingForUpNext() {
        val viewModel = viewModel()
        viewModel.onIntent(PlayerIntent.TogglePlayPause)
        viewModel.onIntent(PlayerIntent.SeekTo(90.seconds))

        viewModel.onIntent(PlayerIntent.SelectUpNext("v2"))

        assertEquals(90.seconds, positions().last())
        assertNull(player.state.value.videoId)
    }

    @Test
    fun recordsThePlayersDuration() {
        player.loadStatus = PlaybackStatus.BUFFERING
        viewModel()

        player.report { it.copy(status = PlaybackStatus.READY, duration = 10.minutes + 3.seconds) }

        assertEquals(listOf(Record("v1", Duration.ZERO, 10.minutes + 3.seconds)), history.records)
    }

    @Test
    fun liveIsRecordedOnceWithoutCheckpoints() {
        viewModel("live")

        player.report { it.copy(position = 30.seconds) }
        player.report { it.copy(position = 60.seconds) }

        assertEquals(listOf(Record("live", Duration.ZERO, null)), history.records)
    }

    @Test
    fun aReplacedScreenRecordsNothing() {
        val store = ViewModelStore()
        val stale = storedViewModel(store, "v1")
        viewModel("v2")
        val staleRecords = positions("v1")

        player.report { it.copy(position = 40.seconds) }
        stale.onIntent(PlayerIntent.ScreenHidden)
        store.clear()

        assertEquals(staleRecords, positions("v1"))
        assertEquals("v2", player.state.value.videoId)
    }
}
