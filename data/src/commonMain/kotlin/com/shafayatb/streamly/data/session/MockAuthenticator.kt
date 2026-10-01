package com.shafayatb.streamly.data.session

import com.shafayatb.streamly.domain.session.AuthProvider
import com.shafayatb.streamly.domain.session.User

/** Stands in for an auth backend: the brief allows mocked sign-in, so accounts are built locally. */
internal object MockAuthenticator {

    val googleAccount: User = User(
        name = "Anika Rahman",
        email = "anika@streamly.app",
        provider = AuthProvider.GOOGLE,
    )

    fun emailAccount(email: String): User {
        val address = email.trim()
        return User(
            name = displayNameFor(address),
            email = address,
            provider = AuthProvider.EMAIL,
        )
    }

    private fun displayNameFor(email: String): String {
        val words = email.substringBefore('@')
            .split('.', '_', '-', '+')
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return email
        return words.joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
    }
}
