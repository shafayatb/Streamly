package com.shafayatb.streamly.domain.session

/**
 * Says whose downloads are whose. Authentication is mocked, so the email is the account's
 * identity; the prefix keeps it apart from the guest key.
 */
public fun Session.accountKey(): String = when (this) {
    is Session.SignedIn -> "email:${user.email.trim().lowercase()}"
    Session.Guest -> "guest"
}
