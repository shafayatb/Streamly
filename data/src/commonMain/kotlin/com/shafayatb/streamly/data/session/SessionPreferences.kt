package com.shafayatb.streamly.data.session

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shafayatb.streamly.domain.session.AuthProvider
import com.shafayatb.streamly.domain.session.Session
import com.shafayatb.streamly.domain.session.User

internal object SessionKeys {
    val type = stringPreferencesKey("session_type")
    val userName = stringPreferencesKey("user_name")
    val userEmail = stringPreferencesKey("user_email")
    val userProvider = stringPreferencesKey("user_provider")
}

private const val TYPE_GUEST = "guest"
private const val TYPE_SIGNED_IN = "signed_in"

/** Returns `null` for an empty or incomplete store so a damaged session falls back to onboarding. */
internal fun Preferences.toSession(): Session? = when (this[SessionKeys.type]) {
    TYPE_GUEST -> Session.Guest
    TYPE_SIGNED_IN -> toUser()?.let(Session::SignedIn)
    else -> null
}

private fun Preferences.toUser(): User? {
    val name = this[SessionKeys.userName] ?: return null
    val email = this[SessionKeys.userEmail] ?: return null
    val provider = this[SessionKeys.userProvider]
        ?.let { stored -> AuthProvider.entries.firstOrNull { it.name == stored } }
        ?: return null
    return User(name = name, email = email, provider = provider)
}

internal fun MutablePreferences.writeSession(session: Session) {
    clear()
    when (session) {
        Session.Guest -> this[SessionKeys.type] = TYPE_GUEST
        is Session.SignedIn -> {
            this[SessionKeys.type] = TYPE_SIGNED_IN
            this[SessionKeys.userName] = session.user.name
            this[SessionKeys.userEmail] = session.user.email
            this[SessionKeys.userProvider] = session.user.provider.name
        }
    }
}
