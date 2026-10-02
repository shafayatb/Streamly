package com.shafayatb.streamly.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class InitialsTest {

    @Test
    fun firstAndLastNames() {
        assertEquals("AR", initialsOf("Anika Rahman"))
        assertEquals("JD", initialsOf("jane mary doe"))
    }

    @Test
    fun oneNameOrNone() {
        assertEquals("J", initialsOf("jane"))
        assertEquals("", initialsOf("   "))
    }
}
