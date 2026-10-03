package com.shafayatb.streamly.navigation

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.shafayatb.streamly.player.OrientationLock
import com.shafayatb.streamly.player.OrientationLockEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OrientationReleaseTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun leavingThePlayerHandsTheOrientationBackBeforeItsExitAnimationEnds() {
        val backStack = mutableStateListOf<Route>(Route.Home, Route.Player("v1"))
        var playerComposed = false
        rule.setContent {
            OrientationReleaseEffect(topRoute = backStack.lastOrNull())
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeAt(backStack.lastIndex) },
                entryProvider = { route ->
                    NavEntry(route) {
                        if (route is Route.Player) {
                            // A portrait lock on a portrait test device: nothing rotates or recreates.
                            OrientationLockEffect(OrientationLock.PORTRAIT)
                            DisposableEffect(Unit) {
                                playerComposed = true
                                onDispose { playerComposed = false }
                            }
                        }
                    }
                },
            )
        }
        rule.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, rule.activity.requestedOrientation)

        rule.mainClock.autoAdvance = false
        rule.runOnIdle { backStack.removeAt(backStack.lastIndex) }
        rule.mainClock.advanceTimeBy(100)

        // A rotation landing now would recreate the activity before the Player is disposed.
        assertTrue("the popped Player is still in its exit animation", playerComposed)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, rule.activity.requestedOrientation)
    }
}
