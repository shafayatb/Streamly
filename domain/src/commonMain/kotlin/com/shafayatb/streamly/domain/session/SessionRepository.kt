package com.shafayatb.streamly.domain.session

import com.shafayatb.streamly.domain.util.DataError
import com.shafayatb.streamly.domain.util.EmptyResult
import kotlinx.coroutines.flow.Flow

/**
 * Owns the persisted session. Authentication is mocked, so signing in only stores a session;
 * implementations must not perform real auth calls.
 */
public interface SessionRepository {
    /** The current session, or `null` when nobody is signed in. */
    public val session: Flow<Session?>

    public suspend fun signInWithGoogle(): EmptyResult<DataError.Local>

    /** [email] must already be valid; see [com.shafayatb.streamly.domain.validation.EmailValidator]. */
    public suspend fun signInWithEmail(email: String): EmptyResult<DataError.Local>

    public suspend fun continueAsGuest(): EmptyResult<DataError.Local>

    public suspend fun signOut(): EmptyResult<DataError.Local>
}
