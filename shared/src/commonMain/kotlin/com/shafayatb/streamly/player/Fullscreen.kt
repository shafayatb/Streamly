package com.shafayatb.streamly.player

import androidx.compose.runtime.Immutable
import androidx.window.core.layout.WindowSizeClass

/** The window as fullscreen sees it. */
enum class WindowShape {
    /** Compact width, such as a phone held upright. Fullscreen rotates it to landscape. */
    PHONE_PORTRAIT,

    /** Short height, such as a phone held sideways. The video fills it unless the user left fullscreen. */
    PHONE_LANDSCAPE,

    /** A tablet or an unfolded foldable. Fullscreen hides the system bars without rotating. */
    LARGE,
}

/** How the phone is physically held, whatever the screen shows. */
enum class DeviceOrientation { PORTRAIT, LANDSCAPE, UNKNOWN }

/** The orientation the Player asks the system for. */
enum class OrientationLock { NONE, LANDSCAPE, PORTRAIT }

@Immutable
data class FullscreenState(
    val isFullscreen: Boolean = false,
    val orientationLock: OrientationLock = OrientationLock.NONE,
    /** The window the screen last reported; `null` until its first report. */
    val window: WindowShape? = null,
)

sealed interface FullscreenInput {
    /** The "Full screen" button. */
    data object Enter : FullscreenInput

    /** The "Exit full screen" button, or Back while fullscreen. */
    data object Exit : FullscreenInput
    data class WindowChanged(val shape: WindowShape) : FullscreenInput
    data class DeviceChanged(val orientation: DeviceOrientation, val autoRotate: Boolean) : FullscreenInput
}

/**
 * The fullscreen rule. Like YouTube, a phone's lock lasts only until the phone is held that way;
 * then the sensor takes over again, so turning it upright leaves fullscreen. With auto-rotate
 * off the sensor never takes over, so the lock lasts until the user exits or leaves the Player.
 */
fun FullscreenState.reduce(input: FullscreenInput): FullscreenState = when (input) {
    // Each is a no-op once applied, so a second tap before the screen turns cannot undo the first.
    FullscreenInput.Enter -> if (isFullscreen) this else enter()
    FullscreenInput.Exit -> if (isFullscreen) exit() else this
    is FullscreenInput.WindowChanged -> onWindow(input.shape)
    is FullscreenInput.DeviceChanged -> onDevice(input.orientation, input.autoRotate)
}

/**
 * Whether [window] shows the video fullscreen. The screen reports a new window a frame after
 * composing it, so the layout asks what the report will lead to rather than flash inline first.
 * While a requested rotation is on its way the window keeps its layout: switching first would
 * rebuild the video surface mid-rotation, and the system holds the rotation until it draws.
 */
fun FullscreenState.showsFullscreenIn(window: WindowShape): Boolean {
    val next = reduce(FullscreenInput.WindowChanged(window))
    return when {
        next.orientationLock == OrientationLock.LANDSCAPE && window == WindowShape.PHONE_PORTRAIT -> false
        next.orientationLock == OrientationLock.PORTRAIT && window == WindowShape.PHONE_LANDSCAPE -> true
        else -> next.isFullscreen
    }
}

private fun FullscreenState.enter() = copy(
    isFullscreen = true,
    orientationLock = if (window == WindowShape.PHONE_PORTRAIT) OrientationLock.LANDSCAPE else OrientationLock.NONE,
)

private fun FullscreenState.exit() = copy(
    isFullscreen = false,
    orientationLock = if (window == WindowShape.PHONE_LANDSCAPE) OrientationLock.PORTRAIT else OrientationLock.NONE,
)

private fun FullscreenState.onWindow(shape: WindowShape): FullscreenState {
    val reported = copy(window = shape)
    // While a lock is held the window is catching up with the user's choice, which stands.
    if (orientationLock != OrientationLock.NONE) return reported
    return when (shape) {
        WindowShape.PHONE_LANDSCAPE -> reported.copy(isFullscreen = true)
        WindowShape.PHONE_PORTRAIT -> reported.copy(isFullscreen = false)
        WindowShape.LARGE -> reported
    }
}

private fun FullscreenState.onDevice(orientation: DeviceOrientation, autoRotate: Boolean): FullscreenState {
    val heldTheLockedWay = when (orientationLock) {
        OrientationLock.NONE -> return this
        OrientationLock.LANDSCAPE -> orientation == DeviceOrientation.LANDSCAPE
        OrientationLock.PORTRAIT -> orientation == DeviceOrientation.PORTRAIT
    }
    return if (autoRotate && heldTheLockedWay) copy(orientationLock = OrientationLock.NONE) else this
}

private const val AXIS_TOLERANCE_DEGREES = 30

/**
 * Quantises an `OrientationEventListener` reading: [degrees] clockwise from the natural
 * (portrait) orientation, or -1 when the phone lies flat. Readings within 30° of an axis count;
 * the ones between keep [previous], so a phone held near 45° does not flicker.
 */
fun deviceOrientationOf(degrees: Int, previous: DeviceOrientation): DeviceOrientation {
    if (degrees < 0) return DeviceOrientation.UNKNOWN
    val fromAxis = degrees % 90
    if (fromAxis in (AXIS_TOLERANCE_DEGREES + 1) until (90 - AXIS_TOLERANCE_DEGREES)) return previous
    val axis = (degrees + 45) / 90 % 4
    return if (axis % 2 == 0) DeviceOrientation.PORTRAIT else DeviceOrientation.LANDSCAPE
}

fun WindowSizeClass.toWindowShape(): WindowShape = when {
    !isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) -> WindowShape.PHONE_LANDSCAPE
    !isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> WindowShape.PHONE_PORTRAIT
    else -> WindowShape.LARGE
}

/**
 * Android ignores orientation requests in split-screen and freeform windows, so there a portrait
 * window is treated like a tablet's: fullscreen only hides the bars.
 */
fun windowShapeOf(sizeClass: WindowSizeClass, isInMultiWindow: Boolean): WindowShape {
    val shape = sizeClass.toWindowShape()
    return if (isInMultiWindow && shape == WindowShape.PHONE_PORTRAIT) WindowShape.LARGE else shape
}
