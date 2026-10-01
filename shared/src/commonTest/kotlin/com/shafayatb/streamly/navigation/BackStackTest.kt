package com.shafayatb.streamly.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class BackStackTest {

    @Test
    fun resetToLeavesOnlyTheNewRoute() {
        val backStack = mutableListOf<Route>(Route.Onboarding, Route.EmailSignIn)

        backStack.resetTo(Route.Home)

        assertEquals(listOf<Route>(Route.Home), backStack)
    }

    @Test
    fun popIfNotRootRemovesTheTopDestination() {
        val backStack = mutableListOf<Route>(Route.Onboarding, Route.EmailSignIn)

        backStack.popIfNotRoot()

        assertEquals(listOf<Route>(Route.Onboarding), backStack)
    }

    @Test
    fun popIfNotRootKeepsTheRoot() {
        val backStack = mutableListOf<Route>(Route.Home)

        backStack.popIfNotRoot()

        assertEquals(listOf<Route>(Route.Home), backStack)
    }
}
