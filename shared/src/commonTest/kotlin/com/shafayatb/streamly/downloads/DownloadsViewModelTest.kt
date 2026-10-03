package com.shafayatb.streamly.downloads

import app.cash.turbine.test
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.download.DownloadStatus
import com.shafayatb.streamly.domain.download.StorageUsage
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.testing.FakeDownloadRepository
import com.shafayatb.streamly.testing.testDownload
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.downloads_detail
import streamly.shared.generated.resources.downloads_progress
import streamly.shared.generated.resources.downloads_storage
import streamly.shared.generated.resources.error_storage_unknown

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val repository = FakeDownloadRepository()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = DownloadsViewModel(repository)

    private fun DownloadsViewModel.items() = (state.value.content as DownloadsContent.Loaded).items

    @Test
    fun loadsUntilTheDownloadsAreRead() {
        assertEquals(DownloadsContent.Loading, viewModel().state.value.content)
    }

    @Test
    fun noDownloadsIsEmptyWithTheStorageFigure() {
        repository.emit()
        repository.storageUsage.value = StorageUsage(usedBytes = 0, freeBytes = 3_100_000_000)

        val state = viewModel().state.value

        assertEquals(DownloadsContent.Empty, state.content)
        assertEquals(UiText.Resource(Res.string.downloads_storage, listOf("0 B", "3.1 GB")), state.storage)
    }

    @Test
    fun aReadFailureShowsAnErrorAndTryAgainReadsAgain() {
        repository.fail(DataError.Local.UNKNOWN)
        val viewModel = viewModel()
        assertEquals(
            DownloadsContent.Error(UiText.Resource(Res.string.error_storage_unknown)),
            viewModel.state.value.content,
        )

        repository.emit(testDownload("v1"))
        viewModel.onIntent(DownloadsIntent.RetryLoad)

        assertEquals(listOf("v1"), viewModel.items().map { it.videoId })
        assertEquals(2, repository.collections)
    }

    @Test
    fun anInProgressDownloadShowsItsRealProgress() {
        repository.emit(testDownload("v1", DownloadStatus.DOWNLOADING, percent = 25.9f, bytesDownloaded = 28_000_000))

        val status = viewModel().items().single().status

        assertEquals(
            DownloadItemStatus.Downloading(
                progress = 25.9f / 100f,
                text = UiText.Resource(Res.string.downloads_progress, listOf("25%", "28 MB")),
            ),
            status,
        )
    }

    @Test
    fun anUnknownPercentIsIndeterminate() {
        repository.emit(testDownload("v1", DownloadStatus.DOWNLOADING, percent = null, bytesDownloaded = 512))

        val status = assertIs<DownloadItemStatus.Downloading>(viewModel().items().single().status)

        assertNull(status.progress)
        assertEquals(UiText.Resource(Res.string.downloads_progress, listOf("…", "512 B")), status.text)
    }

    @Test
    fun aDownloadHeldForWifiSaysSo() {
        repository.emit(testDownload("wifi", DownloadStatus.WAITING_FOR_WIFI))

        assertEquals(DownloadItemStatus.WaitingForWifi, viewModel().items().single().status)
    }

    @Test
    fun completedDownloadsAreReadyToPlayAfterTheOnesInProgress() {
        repository.emit(
            testDownload("done", DownloadStatus.COMPLETED, bytesDownloaded = 66_000_000, duration = 10.minutes + 34.seconds),
            testDownload("queued", DownloadStatus.QUEUED),
            testDownload("failed", DownloadStatus.FAILED),
            testDownload("waiting", DownloadStatus.WAITING_FOR_NETWORK),
        )

        val items = viewModel().items()

        assertEquals(listOf("queued", "failed", "waiting", "done"), items.map { it.videoId })
        assertEquals(DownloadItemStatus.Queued, items[0].status)
        assertEquals(DownloadItemStatus.Failed, items[1].status)
        assertEquals(DownloadItemStatus.WaitingForNetwork, items[2].status)
        assertEquals(
            DownloadItemStatus.Completed(UiText.Resource(Res.string.downloads_detail, listOf("66 MB", "10:34"))),
            items[3].status,
        )
    }

    @Test
    fun downloadsBeingRemovedAreHidden() {
        repository.emit(testDownload("v1", DownloadStatus.REMOVING))

        assertEquals(DownloadsContent.Empty, viewModel().state.value.content)
    }

    @Test
    fun openingACompletedDownloadPlaysIt() = runTest {
        repository.emit(testDownload("done"), testDownload("busy", DownloadStatus.DOWNLOADING))
        val viewModel = viewModel()

        viewModel.events.test {
            viewModel.onIntent(DownloadsIntent.Open("busy"))
            viewModel.onIntent(DownloadsIntent.Open("done"))
            assertEquals(DownloadsEvent.NavigateToPlayer("done"), awaitItem())
        }
    }

    @Test
    fun cancelAndRetryGoStraightToTheDownloader() {
        repository.emit(testDownload("busy", DownloadStatus.DOWNLOADING), testDownload("failed", DownloadStatus.FAILED))
        val viewModel = viewModel()

        viewModel.onIntent(DownloadsIntent.Cancel("busy"))
        viewModel.onIntent(DownloadsIntent.Retry("failed"))

        assertEquals(listOf("busy"), repository.removed)
        assertEquals(listOf("failed"), repository.retried)
    }

    @Test
    fun removingAsksFirst() {
        repository.emit(testDownload("v1", title = "Weekly recap"))
        val viewModel = viewModel()

        viewModel.onIntent(DownloadsIntent.RequestRemove("v1"))
        assertEquals("Weekly recap", viewModel.state.value.pendingRemoval?.title)
        assertTrue(repository.removed.isEmpty())

        viewModel.onIntent(DownloadsIntent.ConfirmRemove)
        assertEquals(listOf("v1"), repository.removed)
        assertNull(viewModel.state.value.pendingRemoval)
    }

    @Test
    fun dismissingTheDialogKeepsTheDownload() {
        repository.emit(testDownload("v1"))
        val viewModel = viewModel()

        viewModel.onIntent(DownloadsIntent.RequestRemove("v1"))
        viewModel.onIntent(DownloadsIntent.DismissRemove)

        assertNull(viewModel.state.value.pendingRemoval)
        assertTrue(repository.removed.isEmpty())
    }

    @Test
    fun dialogClosesWhenItsDownloadDisappears() {
        repository.emit(testDownload("v1"))
        val viewModel = viewModel()
        viewModel.onIntent(DownloadsIntent.RequestRemove("v1"))

        repository.emit()

        assertNull(viewModel.state.value.pendingRemoval)
    }

    @Test
    fun aDialogWhoseDownloadDisappearedDoesNotReturnWithIt() {
        repository.emit(testDownload("v1"))
        val viewModel = viewModel()
        viewModel.onIntent(DownloadsIntent.RequestRemove("v1"))

        repository.emit()
        repository.emit(testDownload("v1"))

        assertNull(viewModel.state.value.pendingRemoval)
    }
}
