package com.shafayatb.streamly.testing

import com.shafayatb.streamly.domain.session.AuthProvider
import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.session.SessionRepository
import com.shafayatb.streamly.domain.session.User
import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeSessionRepository(initial: Session? = null) : SessionRepository {

    private val sessionFlow = MutableStateFlow(initial)
    override val session: StateFlow<Session?> = sessionFlow

    /** Simulates the stored session changing outside the code under test. */
    fun setSession(session: Session?) {
        sessionFlow.value = session
    }

    /** When set, every write fails with this error instead of storing a session. */
    var failure: DataError.Local? = null

    /** When set, writes suspend until it completes, so tests can observe in-flight state. */
    var gate: CompletableDeferred<Unit>? = null

    val emailSignIns = mutableListOf<String>()
    var writeCount = 0
        private set

    override suspend fun signInWithGoogle(): EmptyResult<DataError.Local> =
        write(Session.SignedIn(User("Anika Rahman", "anika@streamly.app", AuthProvider.GOOGLE)))

    override suspend fun signInWithEmail(email: String): EmptyResult<DataError.Local> {
        emailSignIns += email
        return write(Session.SignedIn(User(email.substringBefore('@'), email, AuthProvider.EMAIL)))
    }

    override suspend fun continueAsGuest(): EmptyResult<DataError.Local> = write(Session.Guest)

    override suspend fun signOut(): EmptyResult<DataError.Local> = write(null)

    private suspend fun write(session: Session?): EmptyResult<DataError.Local> {
        writeCount++
        gate?.await()
        failure?.let { return Result.Failure(it) }
        sessionFlow.value = session
        return Result.Success(Unit)
    }
}
