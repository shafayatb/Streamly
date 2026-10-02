package com.shafayatb.streamly.data.download

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.shafayatb.streamly.domain.util.Result
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreDownloadOwnershipRepositoryTest {

    private val directory: File = createTempDirectory("owners-test").toFile()
    private val storeFile = File(directory, "download_owners.preferences_pb")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun TestScope.newDataStore(job: Job): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + job),
            produceFile = { storeFile },
        )

    @Test
    fun anEmptyStoreHasNoOwners() = runTest {
        val repository = DataStoreDownloadOwnershipRepository(newDataStore(Job()))

        assertEquals(Result.Success(emptyMap()), repository.owners.first())
    }

    @Test
    fun eachAccountIsRecordedOnce() = runTest {
        val repository = DataStoreDownloadOwnershipRepository(newDataStore(Job()))

        repository.addOwner("v", "email:a@x.com")
        repository.addOwner("v", "email:a@x.com")
        assertEquals(Result.Success(Unit), repository.addOwner("v", "guest"))

        assertEquals(Result.Success(mapOf("v" to setOf("email:a@x.com", "guest"))), repository.owners.first())
    }

    @Test
    fun removingAnOwnerReturnsTheOwnersLeft() = runTest {
        val repository = DataStoreDownloadOwnershipRepository(newDataStore(Job()))
        repository.addOwner("v", "email:a@x.com")
        repository.addOwner("v", "guest")

        assertEquals(Result.Success(setOf("guest")), repository.removeOwner("v", "email:a@x.com"))
        assertEquals(Result.Success(emptySet()), repository.removeOwner("v", "guest"))
        assertEquals(Result.Success(emptyMap()), repository.owners.first())
    }

    @Test
    fun ownersSurviveARestart() = runTest {
        val firstJob = Job()
        DataStoreDownloadOwnershipRepository(newDataStore(firstJob)).addOwner("v", "guest")
        firstJob.cancel()

        val restarted = DataStoreDownloadOwnershipRepository(newDataStore(Job()))

        assertEquals(Result.Success(mapOf("v" to setOf("guest"))), restarted.owners.first())
    }
}
