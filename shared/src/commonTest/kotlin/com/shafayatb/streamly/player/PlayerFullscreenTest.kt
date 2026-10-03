package com.shafayatb.streamly.player

import app.cash.turbine.test
import com.shafayatb.streamly.testing.FakeDownloadRepository
import com.shafayatb.streamly.testing.FakeVideoPlayer
import com.shafayatb.streamly.testing.FakeVideoRepository
import com.shafayatb.streamly.testing.FakeWatchHistoryRepository
import com.shafayatb.streamly.testing.testVideo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerFullscreenTest {

    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-01T00:00:00Z")
    }
    private val player = FakeVideoPlayer()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PlayerViewModel(
        videoId = "v1",
        videoRepository = FakeVideoRepository(videos = listOf(testVideo(id = "v1", duration = 10.minutes))),
        videoPlayer = player,
        downloadRepository = FakeDownloadRepository().apply { emit() },
        watchHistory = FakeWatchHistoryRepository(),
        clock = clock,
    )

    @Test
    fun startsInlineWithNoLock() {
        assertEquals(FullscreenState(), viewModel().state.value.fullscreen)
    }

    @Test
    fun theButtonOnAPhoneInPortraitLocksLandscape() {
        val viewModel = viewModel()
        viewModel.onIntent(PlayerIntent.WindowChanged(WindowShape.PHONE_PORTRAIT))

        viewModel.onIntent(PlayerIntent.EnterFullscreen)

        val fullscreen = viewModel.state.value.fullscreen
        assertTrue(fullscreen.isFullscreen)
        assertEquals(OrientationLock.LANDSCAPE, fullscreen.orientationLock)
    }

    @Test
    fun holdingThePhoneSidewaysReleasesTheLock() {
        val viewModel = viewModel()
        viewModel.onIntent(PlayerIntent.WindowChanged(WindowShape.PHONE_PORTRAIT))
        viewModel.onIntent(PlayerIntent.EnterFullscreen)

        viewModel.onIntent(PlayerIntent.DeviceOrientationChanged(DeviceOrientation.LANDSCAPE, autoRotate = true))

        assertEquals(OrientationLock.NONE, viewModel.state.value.fullscreen.orientationLock)
    }

    @Test
    fun backInFullscreenExitsInsteadOfLeaving() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(PlayerIntent.WindowChanged(WindowShape.PHONE_LANDSCAPE))
        assertTrue(viewModel.state.value.fullscreen.isFullscreen)

        viewModel.events.test {
            viewModel.onIntent(PlayerIntent.NavigateBack)
            expectNoEvents()
            assertFalse(viewModel.state.value.fullscreen.isFullscreen)
            assertEquals(OrientationLock.PORTRAIT, viewModel.state.value.fullscreen.orientationLock)

            viewModel.onIntent(PlayerIntent.NavigateBack)
            assertEquals(PlayerEvent.NavigateBack, awaitItem())
        }
    }

    @Test
    fun fullscreenNeverTouchesPlayback() {
        val viewModel = viewModel()
        val before = player.state.value

        viewModel.onIntent(PlayerIntent.WindowChanged(WindowShape.PHONE_PORTRAIT))
        viewModel.onIntent(PlayerIntent.EnterFullscreen)
        viewModel.onIntent(PlayerIntent.WindowChanged(WindowShape.PHONE_LANDSCAPE))
        viewModel.onIntent(PlayerIntent.ExitFullscreen)

        assertEquals(before, player.state.value)
        assertEquals(listOf("v1"), player.loadedVideoIds)
    }
}
