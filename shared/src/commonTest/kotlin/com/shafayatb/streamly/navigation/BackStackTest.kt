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

    @Test
    fun replaceTopSwapsOnlyTheTopDestination() {
        val backStack = mutableListOf<Route>(Route.Home, Route.Player("a"))

        backStack.replaceTop(Route.Player("b"))

        assertEquals(listOf(Route.Home, Route.Player("b")), backStack)
    }

    @Test
    fun selectingATabPutsItOnTopOfHome() {
        val backStack = mutableListOf<Route>(Route.Home)

        backStack.selectTopLevel(Route.Shorts)

        assertEquals(listOf(Route.Home, Route.Shorts), backStack)
    }

    @Test
    fun selectingHomeLeavesTheOtherTab() {
        val backStack = mutableListOf<Route>(Route.Home, Route.Shorts)

        backStack.selectTopLevel(Route.Home)

        assertEquals(listOf<Route>(Route.Home), backStack)
    }

    @Test
    fun selectingTheTabOnTopDoesNothing() {
        val backStack = mutableListOf<Route>(Route.Home, Route.Shorts)

        backStack.selectTopLevel(Route.Shorts)

        assertEquals(listOf(Route.Home, Route.Shorts), backStack)
    }

    @Test
    fun selectingATabDropsWhatWasOpenedOverTheCurrentOne() {
        val backStack = mutableListOf(Route.Home, Route.Player("v1"))

        backStack.selectTopLevel(Route.Shorts)

        assertEquals(listOf(Route.Home, Route.Shorts), backStack)
    }

    @Test
    fun signingOutFromProfileLeavesOnlyOnboarding() {
        val backStack = mutableListOf<Route>(Route.Home, Route.Profile)

        backStack.resetTo(Route.Onboarding)

        assertEquals(listOf<Route>(Route.Onboarding), backStack)
    }

    @Test
    fun profileIsTheFourthTab() {
        assertEquals(TopLevelDestination.PROFILE, TopLevelDestination.of(Route.Profile))
        assertEquals(TopLevelDestination.PROFILE, TopLevelDestination.entries.last())
    }
}
