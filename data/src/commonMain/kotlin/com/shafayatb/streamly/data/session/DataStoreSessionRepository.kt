package com.shafayatb.streamly.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DataStoreSessionRepository(
    private val dataStore: DataStore<Preferences>,
) : SessionRepository {

    // An unreadable store is treated as signed out rather than crashing the app on launch.
    override val session: Flow<Session?> = dataStore.data
        .map { it.toSession() }
        .catch { emit(null) }
        .distinctUntilChanged()

    override suspend fun signInWithGoogle(): EmptyResult<DataError.Local> =
        save(Session.SignedIn(MockAuthenticator.googleAccount))

    override suspend fun signInWithEmail(email: String): EmptyResult<DataError.Local> =
        save(Session.SignedIn(MockAuthenticator.emailAccount(email)))

    override suspend fun continueAsGuest(): EmptyResult<DataError.Local> = save(Session.Guest)

    override suspend fun signOut(): EmptyResult<DataError.Local> = edit { it.clear() }

    private suspend fun save(session: Session): EmptyResult<DataError.Local> =
        edit { it.writeSession(session) }

    private suspend fun edit(transform: (MutablePreferences) -> Unit): EmptyResult<DataError.Local> =
        try {
            dataStore.edit(transform)
            Result.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Failure(e.toLocalError())
        }

    private fun Exception.toLocalError(): DataError.Local =
        if (message?.contains("No space left", ignoreCase = true) == true) {
            DataError.Local.DISK_FULL
        } else {
            DataError.Local.UNKNOWN
        }
}
