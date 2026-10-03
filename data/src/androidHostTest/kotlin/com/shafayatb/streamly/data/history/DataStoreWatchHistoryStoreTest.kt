package com.shafayatb.streamly.data.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shafayatb.streamly.domain.history.WatchHistoryEntry
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.Result
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreWatchHistoryStoreTest {

    private val directory: File = createTempDirectory("history-test").toFile()
    private val storeFile = File(directory, "watch_history.preferences_pb")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun TestScope.newDataStore(job: Job = Job()): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + job),
            produceFile = { storeFile },
        )

    private fun entry(id: String, position: Duration = 1.minutes, duration: Duration? = 10.minutes) = WatchHistoryEntry(
        videoId = id,
        title = "Video $id",
        channelName = "Channel",
        thumbnailUrl = "https://example.com/$id.jpg",
        duration = duration,
        position = position,
        watchedAt = Instant.fromEpochMilliseconds(1_000),
    )

    private suspend fun DataStoreWatchHistoryStore.ids(account: String) =
        (entries(account).first() as Result.Success).data.map { it.videoId }

    @Test
    fun anAccountWithoutHistoryHasNoEntries() = runTest {
        assertEquals(
            Result.Success(emptyList<WatchHistoryEntry>()),
            DataStoreWatchHistoryStore(newDataStore()).entries("guest").first(),
        )
    }

    @Test
    fun theNewestIsFirstAndARewatchMovesToTheTop() = runTest {
        val store = DataStoreWatchHistoryStore(newDataStore())

        store.upsert("guest", entry("a"))
        store.upsert("guest", entry("b"))
        store.upsert("guest", entry("a", position = 4.minutes))

        val entries = (store.entries("guest").first() as Result.Success).data
        assertEquals(listOf("a", "b"), entries.map { it.videoId })
        assertEquals(4.minutes, entries.first().position)
    }

    @Test
    fun anUpdateReplacesTheEntryAtTheTop() = runTest {
        val store = DataStoreWatchHistoryStore(newDataStore())
        store.upsert("guest", entry("a"))
        store.upsert("guest", entry("b"))

        assertEquals(Result.Success(Unit), store.update("guest", entry("a", position = 4.minutes)))

        val entries = (store.entries("guest").first() as Result.Success).data
        assertEquals(listOf("a", "b"), entries.map { it.videoId })
        assertEquals(4.minutes, entries.first().position)
    }

    @Test
    fun anUpdateNeverAddsAVideoBack() = runTest {
        val store = DataStoreWatchHistoryStore(newDataStore())
        store.upsert("guest", entry("a"))
        store.remove("guest", "a")

        assertEquals(Result.Success(Unit), store.update("guest", entry("a", position = 4.minutes)))

        assertEquals(emptyList(), store.ids("guest"))
    }

    @Test
    fun historyKeepsTheNewest100() = runTest {
        val store = DataStoreWatchHistoryStore(newDataStore())

        repeat(105) { store.upsert("guest", entry("v$it")) }

        val ids = store.ids("guest")
        assertEquals(100, ids.size)
        assertEquals("v104", ids.first())
        assertEquals("v5", ids.last())
    }

    @Test
    fun accountsAreKeptApart() = runTest {
        val store = DataStoreWatchHistoryStore(newDataStore())

        store.upsert("email:a@x.com", entry("a"))
        store.upsert("guest", entry("b"))

        assertEquals(listOf("a"), store.ids("email:a@x.com"))
        assertEquals(listOf("b"), store.ids("guest"))
    }

    @Test
    fun removeDropsOneEntryAndClearDropsOneAccount() = runTest {
        val store = DataStoreWatchHistoryStore(newDataStore())
        store.upsert("guest", entry("a"))
        store.upsert("guest", entry("b"))
        store.upsert("email:a@x.com", entry("c"))

        assertEquals(Result.Success(Unit), store.remove("guest", "a"))
        assertEquals(listOf("b"), store.ids("guest"))
        assertEquals(Result.Success(Unit), store.clear("guest"))
        assertEquals(emptyList(), store.ids("guest"))
        assertEquals(listOf("c"), store.ids("email:a@x.com"))
    }

    @Test
    fun everyFieldSurvivesARestart() = runTest {
        val firstJob = Job()
        val live = entry("live", position = Duration.ZERO, duration = null)
        DataStoreWatchHistoryStore(newDataStore(firstJob)).run {
            upsert("guest", entry("a", position = 3.minutes))
            upsert("guest", live)
        }
        firstJob.cancel()

        val restarted = DataStoreWatchHistoryStore(newDataStore())

        assertEquals(Result.Success(listOf(live, entry("a", position = 3.minutes))), restarted.entries("guest").first())
    }

    @Test
    fun aValueThatNoLongerDecodesStartsAfresh() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { it[stringPreferencesKey("history/guest")] = "not json" }
        val store = DataStoreWatchHistoryStore(dataStore)

        assertEquals(Result.Success(emptyList<WatchHistoryEntry>()), store.entries("guest").first())
        store.upsert("guest", entry("a"))
        assertEquals(listOf("a"), store.ids("guest"))
    }

    @Test
    fun aReadErrorIsAFailure() = runTest {
        val store = DataStoreWatchHistoryStore(FailingDataStore(IOException("broken")))

        assertEquals(Result.Failure(DataError.Local.UNKNOWN), store.entries("guest").first())
    }

    @Test
    fun aFullDiskIsReported() = runTest {
        val store = DataStoreWatchHistoryStore(FailingDataStore(IOException("No space left on device")))

        assertEquals(Result.Failure(DataError.Local.DISK_FULL), store.upsert("guest", entry("a")))
    }

    private class FailingDataStore(private val error: IOException) : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw error }
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw error
    }
}
