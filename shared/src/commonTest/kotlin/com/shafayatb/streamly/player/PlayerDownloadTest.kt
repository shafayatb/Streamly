package com.shafayatb.streamly.player

import app.cash.turbine.test
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.download.DownloadError
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.util.Result
import com.shafayatb.streamly.testing.FakeDownloadRepository
import com.shafayatb.streamly.testing.FakeVideoPlayer
import com.shafayatb.streamly.testing.FakeVideoRepository
import com.shafayatb.streamly.testing.FakeWatchHistoryRepository
import com.shafayatb.streamly.testing.testDownload
import com.shafayatb.streamly.testing.testVideo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.download_error_network

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerDownloadTest {

    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-01T00:00:00Z")
    }
    private val video = testVideo(id = "v1")
    private val live = testVideo(id = "live", duration = null)
    private val downloads = FakeDownloadRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        downloads.emit()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(videoId: String = "v1") = PlayerViewModel(
        videoId = videoId,
        videoRepository = FakeVideoRepository(videos = listOf(video, live)),
        videoPlayer = FakeVideoPlayer(),
        downloadRepository = downloads,
        watchHistory = FakeWatchHistoryRepository(),
        clock = clock,
    )

    private val PlayerViewModel.download get() = state.value.download

    @Test
    fun liveVideosHaveNoDownloadAction() {
        assertEquals(DownloadActionUi.Hidden, viewModel(videoId = "live").download)
    }

    @Test
    fun aVideoNotYetDownloadedOffersDownload() {
        assertEquals(DownloadActionUi.Idle, viewModel().download)
    }

    @Test
    fun startingShowsQueuedUntilTheDownloaderReportsIt() {
        val gate = CompletableDeferred<Unit>()
        downloads.gate = gate
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.Download)
        assertEquals(DownloadActionUi.Queued, viewModel.download)
        assertEquals(listOf("v1"), downloads.requested)

        gate.complete(Unit)
        // Queued with the downloader until the service picks it up.
        assertEquals(DownloadActionUi.Queued, viewModel.download)

        downloads.emit(testDownload("v1", DownloadStatus.DOWNLOADING, percent = 42.7f))
        assertEquals(DownloadActionUi.Downloading(percent = 42), viewModel.download)
    }

    @Test
    fun secondTapWhileStartingIsIgnored() {
        downloads.gate = CompletableDeferred()
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.Download)
        viewModel.onIntent(PlayerIntent.Download)

        assertEquals(listOf("v1"), downloads.requested)
    }

    @Test
    fun aFailedStartExplainsWhyAndOffersDownloadAgain() = runTest {
        downloads.downloadResult = Result.Failure(DownloadError.NETWORK)
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(PlayerIntent.Download)
            assertEquals(PlayerEvent.ShowMessage(UiText.Resource(Res.string.download_error_network)), awaitItem())
        }
        assertEquals(DownloadActionUi.Idle, viewModel.download)
    }

    @Test
    fun followsTheDownloadersStatus() {
        val viewModel = viewModel()

        downloads.emit(testDownload("v1", DownloadStatus.QUEUED))
        assertEquals(DownloadActionUi.Queued, viewModel.download)
        downloads.emit(testDownload("v1", DownloadStatus.WAITING_FOR_NETWORK))
        assertEquals(DownloadActionUi.WaitingForNetwork, viewModel.download)
        downloads.emit(testDownload("v1", DownloadStatus.WAITING_FOR_WIFI))
        assertEquals(DownloadActionUi.WaitingForWifi, viewModel.download)
        downloads.emit(testDownload("v1", DownloadStatus.DOWNLOADING, percent = null))
        assertEquals(DownloadActionUi.Downloading(percent = null), viewModel.download)
        downloads.emit(testDownload("v1", DownloadStatus.COMPLETED))
        assertEquals(DownloadActionUi.Downloaded, viewModel.download)
        downloads.emit(testDownload("v1", DownloadStatus.FAILED))
        assertEquals(DownloadActionUi.Failed, viewModel.download)
        downloads.emit(testDownload("v1", DownloadStatus.REMOVING))
        assertEquals(DownloadActionUi.Removing, viewModel.download)
        downloads.emit(testDownload("other", DownloadStatus.COMPLETED))
        assertEquals(DownloadActionUi.Idle, viewModel.download)
    }

    @Test
    fun cancellingWhileStartingStopsTheStart() {
        val gate = CompletableDeferred<Unit>()
        downloads.gate = gate
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.Download)
        viewModel.onIntent(PlayerIntent.CancelDownload)

        assertEquals(DownloadActionUi.Idle, viewModel.download)
        // The repository is told too, so it can drop a start it is still preparing.
        assertEquals(listOf("v1"), downloads.removed)
        gate.complete(Unit)
        assertTrue(downloads.queued.isEmpty())
        assertEquals(DownloadActionUi.Idle, viewModel.download)
    }

    @Test
    fun cancellingRemovesWithoutAsking() {
        downloads.emit(testDownload("v1", DownloadStatus.DOWNLOADING, percent = 10f))
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.CancelDownload)

        assertEquals(listOf("v1"), downloads.removed)
        assertFalse(viewModel.state.value.isRemoveDownloadDialogShown)
    }

    @Test
    fun removingADownloadAsksFirst() {
        downloads.emit(testDownload("v1", DownloadStatus.COMPLETED))
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.RequestRemoveDownload)
        assertTrue(viewModel.state.value.isRemoveDownloadDialogShown)
        assertTrue(downloads.removed.isEmpty())

        viewModel.onIntent(PlayerIntent.ConfirmRemoveDownload)
        assertEquals(listOf("v1"), downloads.removed)
        assertFalse(viewModel.state.value.isRemoveDownloadDialogShown)
    }

    @Test
    fun dismissingTheDialogKeepsTheDownload() {
        downloads.emit(testDownload("v1", DownloadStatus.COMPLETED))
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.RequestRemoveDownload)
        viewModel.onIntent(PlayerIntent.DismissRemoveDownload)

        assertFalse(viewModel.state.value.isRemoveDownloadDialogShown)
        assertTrue(downloads.removed.isEmpty())
    }

    @Test
    fun downloadOnAFailedDownloadRetriesIt() {
        downloads.emit(testDownload("v1", DownloadStatus.FAILED))
        val viewModel = viewModel()

        viewModel.onIntent(PlayerIntent.Download)

        assertEquals(listOf("v1"), downloads.retried)
        assertTrue(downloads.requested.isEmpty())
    }
}
