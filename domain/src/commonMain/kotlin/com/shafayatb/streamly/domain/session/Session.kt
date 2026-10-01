package com.shafayatb.streamly.domain.session

public sealed interface Session {
    public data class SignedIn(val user: User) : Session
    public data object Guest : Session
}

public data class User(
    val name: String,
    val email: String,
    val provider: AuthProvider,
)

public enum class AuthProvider {
    GOOGLE,
    EMAIL,
}
