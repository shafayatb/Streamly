package com.shafayatb.streamly.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.shafayatb.streamly.domain.session.AuthProvider
import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.session.User
import com.shafayatb.streamly.domain.util.Result
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSessionRepositoryTest {

    private val directory: File = createTempDirectory("session-test").toFile()
    private val storeFile = File(directory, "session.preferences_pb")

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    /** Each store gets its own job so a second instance can reopen the same file after a "restart". */
    private fun TestScope.newDataStore(job: Job): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + job),
            produceFile = { storeFile },
        )

    @Test
    fun emptyStoreHasNoSession() = runTest {
        val repository = DataStoreSessionRepository(newDataStore(Job()))

        assertNull(repository.session.first())
    }

    @Test
    fun googleSignInStoresDemoUser() = runTest {
        val repository = DataStoreSessionRepository(newDataStore(Job()))

        assertEquals(Result.Success(Unit), repository.signInWithGoogle())
        assertEquals(Session.SignedIn(MockAuthenticator.googleAccount), repository.session.first())
    }

    @Test
    fun emailSignInStoresEmailUser() = runTest {
        val repository = DataStoreSessionRepository(newDataStore(Job()))

        assertEquals(Result.Success(Unit), repository.signInWithEmail("jane.doe@example.com"))
        assertEquals(
            Session.SignedIn(User("Jane Doe", "jane.doe@example.com", AuthProvider.EMAIL)),
            repository.session.first(),
        )
    }

    @Test
    fun guestReplacesSignedInUser() = runTest {
        val repository = DataStoreSessionRepository(newDataStore(Job()))
        repository.signInWithGoogle()

        assertEquals(Result.Success(Unit), repository.continueAsGuest())
        assertEquals(Session.Guest, repository.session.first())
    }

    @Test
    fun signOutClearsSession() = runTest {
        val repository = DataStoreSessionRepository(newDataStore(Job()))
        repository.signInWithEmail("jane@example.com")

        assertEquals(Result.Success(Unit), repository.signOut())
        assertNull(repository.session.first())
    }

    @Test
    fun sessionSurvivesRestart() = runTest {
        val firstLaunch = Job()
        DataStoreSessionRepository(newDataStore(firstLaunch)).signInWithGoogle()
        firstLaunch.cancel()
        firstLaunch.join()

        val relaunched = DataStoreSessionRepository(newDataStore(Job()))

        assertEquals(Session.SignedIn(MockAuthenticator.googleAccount), relaunched.session.first())
    }

    @Test
    fun incompleteSignedInRecordIsTreatedAsSignedOut() = runTest {
        val dataStore = newDataStore(Job())
        dataStore.edit { it[SessionKeys.type] = "signed_in" }

        assertNull(DataStoreSessionRepository(dataStore).session.first())
    }
}
