package com.shafayatb.streamly.domain.download

import com.shafayatb.streamly.domain.session.AuthProvider
import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.session.User
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDownloadRepositoryTest {

    private val anika = Session.SignedIn(User("Anika Rahman", "anika@streamly.app", AuthProvider.GOOGLE))
    private val jane = Session.SignedIn(User("Jane Doe", "jane@example.com", AuthProvider.EMAIL))
    private val anikaKey = "email:anika@streamly.app"
    private val janeKey = "email:jane@example.com"

    private val device = FakeDeviceDownloads()
    private val ownership = FakeDownloadOwnership()
    private val sessions = FakeSessions(anika)

    private fun TestScope.repository() = AccountDownloadRepository(device, ownership, sessions, backgroundScope)

    private suspend fun AccountDownloadRepository.ids(): List<String> =
        (downloads.first() as Result.Success).data.map { it.videoId }

    @Test
    fun listsOnlyTheSignedInAccountsDownloads() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("a"), download("b"), download("shared"))
        ownership.stored.value = mapOf("a" to setOf(anikaKey), "b" to setOf(janeKey), "shared" to setOf(anikaKey, janeKey))

        assertEquals(listOf("a", "shared"), repository().ids())
    }

    @Test
    fun switchingAccountsShowsThatAccountsDownloads() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("a"), download("b"))
        ownership.stored.value = mapOf("a" to setOf(anikaKey), "b" to setOf(janeKey))
        val repository = repository()

        sessions.session.value = jane

        assertEquals(listOf("b"), repository.ids())
    }

    @Test
    fun signedOutSeesNoDownloads() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("a", DownloadStatus.DOWNLOADING))
        ownership.stored.value = mapOf("a" to setOf(anikaKey))
        val repository = repository()

        sessions.session.value = null

        assertEquals(emptyList(), repository.ids())
    }

    @Test
    fun anUnreadableOwnerStoreIsAnError() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("a"))
        ownership.readFailure.value = DataError.Local.UNKNOWN

        assertEquals(Result.Failure(DataError.Local.UNKNOWN), repository().downloads.first())
    }

    @Test
    fun storageCountsOnlyTheAccountsDownloads() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("a", bytes = 300), download("b", bytes = 50), download("gone", DownloadStatus.REMOVING, bytes = 70))
        ownership.stored.value = mapOf("a" to setOf(anikaKey), "b" to setOf(janeKey), "gone" to setOf(anikaKey))

        assertEquals(StorageUsage(usedBytes = 300, freeBytes = 5_000), repository().storage.first())
    }

    @Test
    fun downloadingRecordsTheOwnerBeforeQueuing() = runTest(UnconfinedTestDispatcher()) {
        device.gate = CompletableDeferred()
        val repository = repository()

        val result = async { repository.download(video("v")) }

        assertEquals(mapOf("v" to setOf(anikaKey)), ownership.stored.value)
        assertEquals(listOf("v"), device.requested)
        device.gate!!.complete(Unit)
        assertEquals(Result.Success(Unit), result.await())
        assertEquals(listOf("v"), device.queued)
    }

    @Test
    fun downloadingAVideoAnotherAccountSavedSharesIt() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("v"))
        ownership.stored.value = mapOf("v" to setOf(janeKey))

        assertEquals(Result.Success(Unit), repository().download(video("v")))

        assertEquals(emptyList(), device.requested)
        assertEquals(setOf(janeKey, anikaKey), ownership.stored.value["v"])
    }

    @Test
    fun downloadingAVideoWaitingForWifiSharesItWithoutQueuingAgain() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("v", DownloadStatus.WAITING_FOR_WIFI))
        ownership.stored.value = mapOf("v" to setOf(janeKey))

        assertEquals(Result.Success(Unit), repository().download(video("v")))

        assertEquals(emptyList(), device.requested)
        assertEquals(setOf(janeKey, anikaKey), ownership.stored.value["v"])
    }

    @Test
    fun downloadingAFailedDownloadRetriesIt() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("v", DownloadStatus.FAILED))
        ownership.stored.value = mapOf("v" to setOf(janeKey))

        repository().download(video("v"))

        assertEquals(listOf("v"), device.retried)
        assertEquals(emptyList(), device.requested)
    }

    @Test
    fun aFailedQueueRemovesTheOwnerAgain() = runTest(UnconfinedTestDispatcher()) {
        device.downloadResult = Result.Failure(DownloadError.NETWORK)

        assertEquals(Result.Failure(DownloadError.NETWORK), repository().download(video("v")))

        assertEquals(emptyMap(), ownership.stored.value)
    }

    @Test
    fun anOwnerWriteFailureDoesNotQueue() = runTest(UnconfinedTestDispatcher()) {
        ownership.writeFailure = DataError.Local.DISK_FULL

        assertEquals(Result.Failure(DownloadError.UNKNOWN), repository().download(video("v")))

        assertEquals(emptyList(), device.requested)
    }

    @Test
    fun liveVideosAreRefusedWithoutAnOwnerRecord() = runTest(UnconfinedTestDispatcher()) {
        assertEquals(Result.Failure(DownloadError.LIVE_NOT_SUPPORTED), repository().download(video("live", duration = null)))

        assertEquals(emptyMap(), ownership.stored.value)
        assertEquals(emptyList(), device.requested)
    }

    @Test
    fun removingASharedDownloadKeepsItForTheOtherAccount() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("v"))
        ownership.stored.value = mapOf("v" to setOf(anikaKey, janeKey))

        repository().remove("v")

        assertEquals(emptyList(), device.removed)
        assertEquals(mapOf("v" to setOf(janeKey)), ownership.stored.value)
    }

    @Test
    fun removingTheLastOwnersDownloadDeletesIt() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("v"))
        ownership.stored.value = mapOf("v" to setOf(anikaKey))

        repository().remove("v")

        assertEquals(listOf("v"), device.removed)
        assertEquals(emptyMap(), ownership.stored.value)
    }

    @Test
    fun removingWhileStartingLeavesNoDownload() = runTest(UnconfinedTestDispatcher()) {
        device.gate = CompletableDeferred()
        val repository = repository()
        val start = async { runCatching { repository.download(video("v")) } }

        repository.remove("v")
        device.gate!!.complete(Unit)
        start.await()

        assertEquals(emptyList(), device.queued)
        assertEquals(listOf("v"), device.removed)
        assertEquals(emptyMap(), ownership.stored.value)
    }

    @Test
    fun retryIsOnlyForTheAccountsOwnDownloads() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("mine", DownloadStatus.FAILED), download("theirs", DownloadStatus.FAILED))
        ownership.stored.value = mapOf("mine" to setOf(anikaKey), "theirs" to setOf(janeKey))
        val repository = repository()

        repository.retry("mine")
        repository.retry("theirs")

        assertEquals(listOf("mine"), device.retried)
    }

    @Test
    fun unownedDownloadsAreAdoptedByTheFirstAccount() = runTest(UnconfinedTestDispatcher()) {
        sessions.session.value = null
        device.set(download("old"), download("theirs"))
        ownership.stored.value = mapOf("theirs" to setOf(janeKey))
        val repository = repository()

        sessions.session.value = anika

        assertEquals(mapOf("old" to setOf(anikaKey), "theirs" to setOf(janeKey)), ownership.stored.value)
        assertEquals(listOf("old"), repository.ids())
    }

    @Test
    fun canPlayOfflineOnlyTheAccountsDownloads() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("mine"), download("theirs"))
        ownership.stored.value = mapOf("mine" to setOf(anikaKey), "theirs" to setOf(janeKey))
        val repository = repository()

        assertTrue(repository.canPlayOffline("mine"))
        assertFalse(repository.canPlayOffline("theirs"))
        sessions.session.value = null
        assertFalse(repository.canPlayOffline("mine"))
    }

    @Test
    fun canPlayOfflineTrustsTheSavedCopyUntilOwnersAreRead() = runTest(UnconfinedTestDispatcher()) {
        ownership.loaded.value = false
        ownership.stored.value = mapOf("theirs" to setOf(janeKey))
        val repository = repository()

        assertTrue(repository.canPlayOffline("theirs"))
        ownership.loaded.value = true
        assertFalse(repository.canPlayOffline("theirs"))
    }

    @Test
    fun removeWorksAfterAFailedOwnersRead() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("v"))
        ownership.stored.value = mapOf("v" to setOf(anikaKey))
        ownership.failNextRead = DataError.Local.UNKNOWN
        val repository = repository()

        repository.remove("v")

        assertEquals(listOf("v"), device.removed)
        assertEquals(emptyMap(), ownership.stored.value)
    }

    @Test
    fun retryWorksAfterAFailedOwnersRead() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("v", DownloadStatus.FAILED))
        ownership.stored.value = mapOf("v" to setOf(anikaKey))
        ownership.failNextRead = DataError.Local.UNKNOWN
        val repository = repository()

        repository.retry("v")

        assertEquals(listOf("v"), device.retried)
    }

    @Test
    fun aDownloadBeingRemovedIsNotAdopted() = runTest(UnconfinedTestDispatcher()) {
        device.set(download("gone", DownloadStatus.REMOVING), download("old"))

        repository()

        assertEquals(mapOf("old" to setOf(anikaKey)), ownership.stored.value)
    }
}
