package com.shafayatb.streamly.domain.session

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountKeyTest {

    @Test
    fun aSignedInAccountIsKeyedByItsEmail() {
        val session = Session.SignedIn(User("Anika Rahman", " Anika@Streamly.app ", AuthProvider.GOOGLE))

        assertEquals("email:anika@streamly.app", session.accountKey())
    }

    @Test
    fun googleAndEmailSignInWithTheSameAddressAreOneAccount() {
        val google = Session.SignedIn(User("Anika Rahman", "anika@streamly.app", AuthProvider.GOOGLE))
        val email = Session.SignedIn(User("Anika", "anika@streamly.app", AuthProvider.EMAIL))

        assertEquals(google.accountKey(), email.accountKey())
    }

    @Test
    fun aGuestHasItsOwnKey() {
        assertEquals("guest", Session.Guest.accountKey())
    }
}
