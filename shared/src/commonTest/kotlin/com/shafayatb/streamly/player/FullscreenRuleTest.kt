package com.shafayatb.streamly.player

import androidx.window.core.layout.WindowSizeClass
import com.shafayatb.streamly.player.DeviceOrientation.LANDSCAPE
import com.shafayatb.streamly.player.DeviceOrientation.PORTRAIT
import com.shafayatb.streamly.player.DeviceOrientation.UNKNOWN
import com.shafayatb.streamly.player.WindowShape.LARGE
import com.shafayatb.streamly.player.WindowShape.PHONE_LANDSCAPE
import com.shafayatb.streamly.player.WindowShape.PHONE_PORTRAIT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullscreenRuleTest {

    private fun FullscreenState.after(vararg inputs: FullscreenInput) = inputs.fold(this) { state, input -> state.reduce(input) }

    private fun window(shape: WindowShape) = FullscreenInput.WindowChanged(shape)
    private fun held(orientation: DeviceOrientation, autoRotate: Boolean = true) =
        FullscreenInput.DeviceChanged(orientation, autoRotate)

    private val phonePortrait = FullscreenState().after(window(PHONE_PORTRAIT))
    private val phoneLandscape = FullscreenState().after(window(PHONE_LANDSCAPE))

    @Test
    fun enteringOnAPhoneInPortraitLocksLandscape() {
        assertEquals(
            FullscreenState(isFullscreen = true, orientationLock = OrientationLock.LANDSCAPE, window = PHONE_PORTRAIT),
            phonePortrait.reduce(FullscreenInput.Enter),
        )
    }

    @Test
    fun enteringOnALargeWindowOnlyGoesImmersive() {
        assertEquals(
            FullscreenState(isFullscreen = true, orientationLock = OrientationLock.NONE, window = LARGE),
            FullscreenState().after(window(LARGE), FullscreenInput.Enter),
        )
    }

    @Test
    fun enteringBeforeTheWindowIsKnownDoesNotRotate() {
        assertEquals(OrientationLock.NONE, FullscreenState().reduce(FullscreenInput.Enter).orientationLock)
    }

    @Test
    fun exitingOnAPhoneInLandscapeLocksPortrait() {
        val exited = phoneLandscape.reduce(FullscreenInput.Exit)

        assertFalse(exited.isFullscreen)
        assertEquals(OrientationLock.PORTRAIT, exited.orientationLock)
    }

    @Test
    fun enteringAgainWhileARotationIsPendingChangesNothing() {
        // A second tap on "Full screen" before the screen turns must not undo the first.
        val entering = phonePortrait.reduce(FullscreenInput.Enter)

        assertEquals(entering, entering.reduce(FullscreenInput.Enter))
    }

    @Test
    fun exitingAgainWhileARotationIsPendingChangesNothing() {
        val exiting = phoneLandscape.reduce(FullscreenInput.Exit)

        assertEquals(exiting, exiting.reduce(FullscreenInput.Exit))
    }

    @Test
    fun exitingOnALargeWindowLocksNothing() {
        val exited = FullscreenState().after(window(LARGE), FullscreenInput.Enter, FullscreenInput.Exit)

        assertEquals(FullscreenState(isFullscreen = false, orientationLock = OrientationLock.NONE, window = LARGE), exited)
    }

    @Test
    fun exitWhenInlineChangesNothing() {
        assertEquals(phonePortrait, phonePortrait.reduce(FullscreenInput.Exit))
    }

    @Test
    fun theLockReleasesOnceThePhoneIsHeldThatWay() {
        val entered = phonePortrait.after(FullscreenInput.Enter, window(PHONE_LANDSCAPE))

        val released = entered.reduce(held(LANDSCAPE))

        assertEquals(OrientationLock.NONE, released.orientationLock)
        assertTrue(released.isFullscreen)
    }

    @Test
    fun theLockHoldsWhileThePhoneIsStillUpright() {
        val entered = phonePortrait.after(FullscreenInput.Enter, window(PHONE_LANDSCAPE))

        assertEquals(entered, entered.reduce(held(PORTRAIT)))
    }

    @Test
    fun aFlatPhoneNeverReleasesTheLock() {
        val entered = phonePortrait.after(FullscreenInput.Enter, window(PHONE_LANDSCAPE))

        assertEquals(entered, entered.reduce(held(UNKNOWN)))
    }

    @Test
    fun theLockHoldsWithAutoRotateOff() {
        val entered = phonePortrait.after(FullscreenInput.Enter, window(PHONE_LANDSCAPE))
        val exited = entered.reduce(FullscreenInput.Exit)

        assertEquals(entered, entered.reduce(held(LANDSCAPE, autoRotate = false)))
        assertEquals(exited, exited.reduce(held(PORTRAIT, autoRotate = false)))
    }

    @Test
    fun aPortraitLockReleasesOnceThePhoneIsUpright() {
        val exited = phoneLandscape.after(FullscreenInput.Exit, window(PHONE_PORTRAIT))

        assertEquals(exited.copy(orientationLock = OrientationLock.NONE), exited.reduce(held(PORTRAIT)))
    }

    @Test
    fun aReadingWithNoLockChangesNothing() {
        assertEquals(phonePortrait, phonePortrait.reduce(held(LANDSCAPE)))
    }

    @Test
    fun rotatingAPhoneToLandscapeEntersFullscreen() {
        assertEquals(FullscreenState(isFullscreen = true, window = PHONE_LANDSCAPE), phoneLandscape)
    }

    @Test
    fun rotatingAPhoneBackToPortraitExits() {
        assertEquals(phonePortrait, phoneLandscape.reduce(window(PHONE_PORTRAIT)))
    }

    @Test
    fun windowChangesWhileLockedKeepTheUsersChoice() {
        val entered = phonePortrait.reduce(FullscreenInput.Enter)

        // The window can report portrait again before the requested rotation lands.
        val stillEntered = entered.reduce(window(PHONE_PORTRAIT))

        assertTrue(stillEntered.isFullscreen)
        assertEquals(OrientationLock.LANDSCAPE, stillEntered.orientationLock)
    }

    @Test
    fun aLargeWindowKeepsTheUsersChoiceWhenItRotates() {
        val entered = FullscreenState().after(window(LARGE), FullscreenInput.Enter)

        assertTrue(entered.reduce(window(LARGE)).isFullscreen)
        assertFalse(FullscreenState().after(window(LARGE), window(LARGE)).isFullscreen)
    }

    @Test
    fun buttonThenTurningThePhoneThenUprightEndsInlineAndUnlocked() {
        val state = phonePortrait.after(
            FullscreenInput.Enter,
            window(PHONE_LANDSCAPE),
            held(LANDSCAPE),
            held(PORTRAIT),
            window(PHONE_PORTRAIT),
        )

        assertEquals(FullscreenState(isFullscreen = false, orientationLock = OrientationLock.NONE, window = PHONE_PORTRAIT), state)
    }

    @Test
    fun rotatedThenBackThenUprightFollowsTheSensorAgain() {
        val upright = phoneLandscape.after(
            FullscreenInput.Exit,
            window(PHONE_PORTRAIT),
            held(LANDSCAPE),
            held(PORTRAIT),
        )
        assertEquals(FullscreenState(isFullscreen = false, orientationLock = OrientationLock.NONE, window = PHONE_PORTRAIT), upright)

        assertTrue(upright.reduce(window(PHONE_LANDSCAPE)).isFullscreen)
    }

    @Test
    fun showsFullscreenInPredictsTheNextWindow() {
        assertTrue(FullscreenState().showsFullscreenIn(PHONE_LANDSCAPE))
        assertFalse(FullscreenState().showsFullscreenIn(PHONE_PORTRAIT))
        assertFalse(phoneLandscape.showsFullscreenIn(PHONE_PORTRAIT))
    }

    @Test
    fun aPendingRotationKeepsTheWindowsLayoutUntilItLands() {
        // Switching layout first would rebuild the video surface mid-rotation and hold the rotation up.
        val entering = phonePortrait.reduce(FullscreenInput.Enter)
        assertFalse(entering.showsFullscreenIn(PHONE_PORTRAIT))
        assertTrue(entering.showsFullscreenIn(PHONE_LANDSCAPE))

        val exiting = phoneLandscape.reduce(FullscreenInput.Exit)
        assertTrue(exiting.showsFullscreenIn(PHONE_LANDSCAPE))
        assertFalse(exiting.showsFullscreenIn(PHONE_PORTRAIT))
    }

    @Test
    fun aPortraitWindowInMultiWindowIsTreatedAsLarge() {
        // Android ignores orientation requests there, so the button can only hide the bars.
        assertEquals(LARGE, windowShapeOf(WindowSizeClass(minWidthDp = 0, minHeightDp = 480), isInMultiWindow = true))
        assertEquals(PHONE_PORTRAIT, windowShapeOf(WindowSizeClass(minWidthDp = 0, minHeightDp = 480), isInMultiWindow = false))
        assertEquals(PHONE_LANDSCAPE, windowShapeOf(WindowSizeClass(minWidthDp = 840, minHeightDp = 0), isInMultiWindow = true))
        assertEquals(LARGE, windowShapeOf(WindowSizeClass(minWidthDp = 600, minHeightDp = 480), isInMultiWindow = true))
    }

    @Test
    fun readingsNearAnAxisSnapToIt() {
        assertEquals(PORTRAIT, deviceOrientationOf(0, UNKNOWN))
        assertEquals(PORTRAIT, deviceOrientationOf(30, UNKNOWN))
        assertEquals(PORTRAIT, deviceOrientationOf(180, UNKNOWN))
        assertEquals(PORTRAIT, deviceOrientationOf(345, UNKNOWN))
        assertEquals(LANDSCAPE, deviceOrientationOf(60, UNKNOWN))
        assertEquals(LANDSCAPE, deviceOrientationOf(90, UNKNOWN))
        assertEquals(LANDSCAPE, deviceOrientationOf(270, UNKNOWN))
    }

    @Test
    fun readingsBetweenAxesKeepThePreviousOrientation() {
        assertEquals(PORTRAIT, deviceOrientationOf(45, PORTRAIT))
        assertEquals(LANDSCAPE, deviceOrientationOf(45, LANDSCAPE))
        assertEquals(UNKNOWN, deviceOrientationOf(315, UNKNOWN))
    }

    @Test
    fun aFlatReadingIsUnknown() {
        assertEquals(UNKNOWN, deviceOrientationOf(-1, LANDSCAPE))
    }

    @Test
    fun windowSizeClassesMapToShapes() {
        assertEquals(PHONE_PORTRAIT, WindowSizeClass(minWidthDp = 0, minHeightDp = 480).toWindowShape())
        assertEquals(PHONE_PORTRAIT, WindowSizeClass(minWidthDp = 0, minHeightDp = 900).toWindowShape())
        assertEquals(PHONE_LANDSCAPE, WindowSizeClass(minWidthDp = 840, minHeightDp = 0).toWindowShape())
        assertEquals(PHONE_LANDSCAPE, WindowSizeClass(minWidthDp = 0, minHeightDp = 0).toWindowShape())
        assertEquals(LARGE, WindowSizeClass(minWidthDp = 600, minHeightDp = 480).toWindowShape())
        assertEquals(LARGE, WindowSizeClass(minWidthDp = 840, minHeightDp = 900).toWindowShape())
    }
}
