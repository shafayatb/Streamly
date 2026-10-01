package com.shafayatb.streamly.data.session

import com.shafayatb.streamly.domain.session.AuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals

class MockAuthenticatorTest {

    @Test
    fun googleAccountIsTheDemoProfile() {
        val user = MockAuthenticator.googleAccount

        assertEquals("Anika Rahman", user.name)
        assertEquals("anika@streamly.app", user.email)
        assertEquals(AuthProvider.GOOGLE, user.provider)
    }

    @Test
    fun emailAccountDerivesDisplayNameFromLocalPart() {
        assertEquals("Jane Doe", MockAuthenticator.emailAccount("jane.doe@example.com").name)
        assertEquals("Sam Lee Dev", MockAuthenticator.emailAccount("sam_lee+dev@example.com").name)
        assertEquals("Anika", MockAuthenticator.emailAccount("anika@streamly.app").name)
    }

    @Test
    fun emailAccountTrimsAddressAndUsesEmailProvider() {
        val user = MockAuthenticator.emailAccount("  jane@example.com ")

        assertEquals("jane@example.com", user.email)
        assertEquals(AuthProvider.EMAIL, user.provider)
    }
}
