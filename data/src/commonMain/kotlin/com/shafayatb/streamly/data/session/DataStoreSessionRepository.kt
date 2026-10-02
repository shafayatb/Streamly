package com.shafayatb.streamly.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shafayatb.streamly.data.local.tryEdit
import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
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

    override suspend fun signOut(): EmptyResult<DataError.Local> = dataStore.tryEdit { it.clear() }

    private suspend fun save(session: Session): EmptyResult<DataError.Local> =
        dataStore.tryEdit { it.writeSession(session) }
}
