# Player Fullscreen Button Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the long-form player a fullscreen / exit-fullscreen button that works with rotation
locked and on tablets. Rotating a phone to landscape still goes fullscreen as it does today. The
phone's orientation follows YouTube's sensor-aware model.

**Architecture:**
- **The rule:** a pure-Kotlin rule in `:shared/commonMain/player` (`Fullscreen.kt`) turns inputs
  into a `FullscreenState`. The inputs are the button, Back, the window shape, and the phone's
  physical orientation with the auto-rotate setting. The state is `isFullscreen` plus the
  orientation to request.
- **The ViewModel:** `PlayerViewModel` keeps the state in `PlayerState.fullscreen` and folds new
  `PlayerIntent`s through the rule.
- **The screen:** `PlayerScreen` derives its layout from that state, shows the toggle beside mute,
  and sends Back to the ViewModel while fullscreen.
- **Android glue:** two expect/actual effects in `:shared/androidMain`. One applies
  `requestedOrientation`. The other reports an `OrientationEventListener` reading only while a lock
  is held.
- **Untouched:** no domain or data change; this is presentation state.

**Tech Stack:** Kotlin 2.4, Compose Multiplatform 1.12 / Material 3, Material 3 adaptive
(`WindowSizeClass`), navigationevent-compose 1.1.0 (`NavigationBackHandler`), Koin 4.2, `kotlin.test`,
`kotlinx-coroutines-test`, Turbine, Compose UI tests.

**Spec:** this file. The design was agreed in chat on October 3 (summary below). The file name
uses October 4 because `FRESH_PROMPT.md` names it.

## Agreed design (brainstorming, October 3)

- **Rotation model (user: sensor-aware, like YouTube).**
  - **Lock and release:** the button locks the orientation until the phone is physically held
    that way, then hands control back to the sensor. Turning the phone upright then exits
    fullscreen.
  - **Exiting in landscape** locks portrait the same way.
  - **With rotation locked** (auto-rotate off) the lock holds until the user exits or leaves the
    Player.
- **Tablets and foldables (user: immersive only).**
  - **Large windows:** a window that is neither compact-width nor short is `LARGE`. There
    fullscreen only hides the bars, with no rotation request. A portrait tablet letterboxes the
    16:9 video.
  - **Why:** Android 16+ ignores orientation requests on screens at least 600 dp wide for apps
    targeting 36+ (this app targets 37), so behaviour is the same on every version.
- **State (user: ViewModel + pure rule).** `PlayerState.fullscreen: FullscreenState`. The new
  intents are `ToggleFullscreen`, `WindowChanged(shape)`, and
  `DeviceOrientationChanged(orientation, autoRotate)`. The ViewModel survives the Activity
  recreation a requested rotation causes, so nothing needs `rememberSaveable`.
- **Back (user: exit fullscreen first).**
  - **Rule:** while fullscreen shows, the system Back and the overlay's back arrow both leave
    fullscreen, however it was entered. A second Back leaves the Player.
  - **Change from today:** Back in phone landscape no longer leaves the Player directly.
- **Button.**
  - **Placement:** in the bottom control row, right of mute.
  - **Icons:** Material `fullscreen` / `fullscreen_exit` as vector drawables.
  - **Labels:** "Full screen" / "Exit full screen".
  - **Availability:** shown whenever the controls are, for live videos too.
- **The rules, exactly:**

  | Input | Window | Result |
  |---|---|---|
  | Toggle (not fullscreen) | `PHONE_PORTRAIT` | fullscreen, lock `LANDSCAPE` |
  | Toggle (not fullscreen) | `LARGE` or not yet reported | fullscreen, lock `NONE` |
  | Toggle / Back (fullscreen) | `PHONE_LANDSCAPE` | inline, lock `PORTRAIT` |
  | Toggle / Back (fullscreen) | `PHONE_PORTRAIT`, `LARGE`, or not yet reported | inline, lock `NONE` |
  | Device reading | any | lock → `NONE` only if auto-rotate is on **and** the phone is held the locked way |
  | Window → `PHONE_LANDSCAPE` | lock `NONE` | fullscreen |
  | Window → `PHONE_PORTRAIT` | lock `NONE` | inline |
  | Window → `LARGE` | lock `NONE` | unchanged |
  | Window → anything | lock held | only the window is recorded (the lock is taking effect) |
  | Player leaves (not a configuration change) | any | the system gets `UNSPECIFIED` back |

### One refinement of the chat design (confirm with the user when the plan is shown)

In chat, section 1 said that exiting in landscape **with auto-rotate off** releases the lock at
once. The rule above instead **locks portrait** in every case. With auto-rotate off it holds that
lock until the user enters fullscreen again or leaves the Player.

- **Common case: same result.** With rotation locked to portrait, holding a portrait lock looks
  exactly like releasing it.
- **Rotation locked to landscape:** this is how the emulator tests landscape
  (`user_rotation 1`). Releasing there would leave the inline layout squeezed into a short
  window, with no way back to portrait. The portrait lock gives the user what they asked for.
- **Simpler rule:** exit no longer needs to know the auto-rotate setting.

### Changes found during implementation and review (October 3)

1. **A pending rotation keeps the window's layout (the device gate found it).**
   - **Problem:** at first, the button switched to the fullscreen layout at once, while the phone
     was still upright. That rebuilt the video surface mid-rotation, and the system held the
     rotation until the new surface drew. The emulator showed about 1.8 s of a black portrait
     fullscreen, with a surface gap of 1.77 s against 0.25 s for a plain rotation.
   - **Change:** `showsFullscreenIn` now keeps the current layout while a requested rotation is on
     its way. A landscape lock in a portrait window stays inline; a portrait lock in a landscape
     window stays fullscreen.
   - **Result:** a gap of 0.22–0.43 s and about 0.6 s from tap to landscape. The video keeps
     playing inline until the screen turns, as on YouTube.
2. **Multi-window: a portrait window is treated as `LARGE` (user, October 3).**
   - **Problem:** Android ignores orientation requests in split-screen and freeform windows. With
     change 1 the button would do nothing visible there.
   - **Change:** `windowShapeOf(sizeClass, isInMultiWindow)` maps a multi-window `PHONE_PORTRAIT`
     to `LARGE`, so the button goes immersive with no lock. Short split windows are unchanged.
   - **Code:** a new expect/actual `isInMultiWindowMode()` in `WindowEffects`. `PlayerScreen` takes
     a `windowShape` parameter (default `currentWindowShape()`), so the device tests pin the
     window.
3. **Explicit Enter and Exit (from the review).** `ToggleFullscreen` became `EnterFullscreen` and
   `ExitFullscreen`, and the input `Toggle` became `Enter`.
   - **Problem:** during a pending rotation the button's label followed the layout, but a toggle
     followed the ViewModel, so a second tap undid the first.
   - **Change:** the button sends the intent its label names, and each input is a no-op once
     applied.
   - **Back:** the handler is also on while the ViewModel is fullscreen, so system Back matches the
     overlay arrow during a pending rotation.
   - **Note:** the task code below still shows `Toggle`; read it as `Enter`.
4. **Release at the navigation level (from the review).**
   - **Problem:** a popped Player stays composed for its exit animation (about 700 ms). An activity
     recreated in that window would dispose it as a configuration change and keep its lock on
     Home.
   - **Change:** `OrientationReleaseEffect(topRoute)` in `AppNavigation` hands the orientation back
     as soon as the Player is not on top. `OrientationLockEffect` no longer resets on dispose.
   - **Pinned by:**
     `OrientationReleaseTest.leavingThePlayerHandsTheOrientationBackBeforeItsExitAnimationEnds`.

## Global Constraints

- Kotlin only, Compose for all UI, MVVM + MVI (one `ViewModel`, one immutable `UiState` as
  `StateFlow`, one sealed intent type, one-off events through a `Channel`).
- `:domain` is untouched. Android APIs (`Activity`, `OrientationEventListener`, `Settings`) stay
  in `:shared/androidMain`; the rule in `:shared/commonMain` imports nothing from Android or Compose
  except `@Immutable` and `WindowSizeClass` (androidx.window core, a KMP data class).
- Every dependency goes through `gradle/libs.versions.toml`. This plan declares one that is
  already on the classpath through Navigation 3:
  `org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.0`.
- Orientation values: lock `LANDSCAPE` → `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`, `PORTRAIT` →
  `SCREEN_ORIENTATION_SENSOR_PORTRAIT`, `NONE` → `SCREEN_ORIENTATION_UNSPECIFIED`.
- Device-reading hysteresis: within **30°** of an axis counts; readings between keep the previous
  value; `-1` (flat) is `UNKNOWN`.
- Strings: `cd_enter_fullscreen` = "Full screen", `cd_exit_fullscreen` = "Exit full screen".
  Compose resource strings use the typographic apostrophe (’), never `\'`.
- Raw tool output: `rtk proxy ./gradlew …`, `rtk proxy git …`. Run Gradle without `-q`.
- No commit or merge without the user's explicit approval. Conventional Commits, with the
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and `Claude-Session:` trailers.

## Review Focus

1. **No rebuffer, no pause, no leak when the button rotates the screen.**
   - Risk: a requested orientation recreates the Activity, the same path as a rotation.
   - Expected: the audio never stops, the position stays continuous, and there is no new load.
     After several toggles and a Back, the heap holds one `MainActivity`.
   - Pinned by: device steps L1–L3. The host suite cannot recreate an Activity.
2. **Exactly one recreation per toggle (no orientation ping-pong).**
   - Risk: `OrientationLockEffect` must not hand `UNSPECIFIED` to the system during the recreation
     it caused. If it did, the new Activity would rotate back and forth.
   - How: it restores `UNSPECIFIED` only when the Activity is not changing configurations, and it
     never resets between two locks (it sets the new value in a `LaunchedEffect`, not a keyed
     `DisposableEffect`).
   - Pinned by: device step L2 (`wm_relaunch_resume_activity` count) and the review of Task 4,
     Step 2.
3. **Back exits fullscreen first, and only then leaves the Player.**
   - Risk: the Player's `NavigationBackHandler` must win over `NavDisplay`'s own handler, and only
     while fullscreen.
   - Expected: inline Back still pops at once.
   - Pinned by: `backInFullscreenGoesToTheViewModel` (Task 3, device test),
     `backInFullscreenExitsInsteadOfLeaving` (Task 2), and device steps B1–B3.
4. **Leaving the Player always gives the orientation back.**
   - Risk: the Player is left by up next, by Back from portrait, or after a lock was taken and not
     yet released.
   - Expected: Home is never stuck in landscape or portrait, and auto-rotate works again there.
   - Pinned by: device steps R3 and R4. The effect's `onDispose` is reviewed in Task 4.
5. **No inline flash on the first frame of a rotation or of opening the Player in landscape, and
   no layout switch before a requested rotation lands.**
   - Risk: the ViewModel learns the window one frame after composition.
   - How: the layout uses `showsFullscreenIn(currentWindow)`, which predicts the reduced state.
   - Pinned by: `showsFullscreenInPredictsTheNextWindow` and
     `aPendingRotationKeepsTheWindowsLayoutUntilItLands` (Task 1), and device step R1 (screen
     recording contact sheet, surface gap).

## File structure

- **Create** `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/Fullscreen.kt`.
  It holds `WindowShape`, `DeviceOrientation`, `OrientationLock`, `FullscreenState`,
  `FullscreenInput`, `reduce`, `showsFullscreenIn`, `deviceOrientationOf`, and
  `WindowSizeClass.toWindowShape()`. It is the whole rule, with no Compose runtime and no Android.
- **Create** `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/FullscreenPlatform.kt`.
  It holds `expect` `OrientationLockEffect(lock)` and `DeviceOrientationEffect(lock, onChange)`.
- **Create** `shared/src/androidMain/kotlin/com/shafayatb/streamly/player/FullscreenPlatform.android.kt`,
  the actuals.
- **Modify** `shared/src/androidMain/kotlin/com/shafayatb/streamly/core/presentation/WindowEffects.android.kt`:
  `findActivity` becomes `internal`, so the actuals can reuse it.
- **Modify** these player files:
  - `PlayerState.kt`: add the `fullscreen` field.
  - `PlayerIntent.kt`: three new intents.
  - `PlayerViewModel.kt`: fold the intents, and make Back exit fullscreen first.
  - `PlayerScreen.kt`: the layout follows the state, plus the Back handler and the effects in the
    Root.
  - `PlayerViewport.kt`: the toggle.
- **Create** `shared/src/commonMain/composeResources/drawable/ic_fullscreen.xml` and
  `ic_fullscreen_exit.xml`.
- **Modify** `shared/src/commonMain/composeResources/values/strings.xml` with the two strings.
- **Modify** `gradle/libs.versions.toml` and `shared/build.gradle.kts`, adding the
  navigationevent-compose catalog entry.
- **Tests:**
  - Create `shared/src/commonTest/kotlin/com/shafayatb/streamly/player/FullscreenRuleTest.kt`.
  - Create `shared/src/commonTest/kotlin/com/shafayatb/streamly/player/PlayerFullscreenTest.kt`,
    for the ViewModel.
  - Create `shared/src/androidDeviceTest/kotlin/com/shafayatb/streamly/player/PlayerFullscreenScreenTest.kt`.
- **Docs:** `README.md` (Player lifecycle table, Adaptive layout, known gaps), `docs/agent-log.md`
  (task 11), and `FRESH_PROMPT.md` (next task: the release gate, now task 12).

---

### Task 1: The fullscreen rule

**Files:**
- Create: `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/Fullscreen.kt`
- Test: `shared/src/commonTest/kotlin/com/shafayatb/streamly/player/FullscreenRuleTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces (later tasks use exactly these):
  - `enum class WindowShape { PHONE_PORTRAIT, PHONE_LANDSCAPE, LARGE }`
  - `enum class DeviceOrientation { PORTRAIT, LANDSCAPE, UNKNOWN }`
  - `enum class OrientationLock { NONE, LANDSCAPE, PORTRAIT }`
  - `data class FullscreenState(val isFullscreen: Boolean = false, val orientationLock: OrientationLock = OrientationLock.NONE, val window: WindowShape? = null)`
  - `sealed interface FullscreenInput { Toggle; Exit; WindowChanged(shape); DeviceChanged(orientation, autoRotate) }`
  - `fun FullscreenState.reduce(input: FullscreenInput): FullscreenState`
  - `fun FullscreenState.showsFullscreenIn(window: WindowShape): Boolean`
  - `fun deviceOrientationOf(degrees: Int, previous: DeviceOrientation): DeviceOrientation`
  - `fun WindowSizeClass.toWindowShape(): WindowShape`

- [ ] **Step 1: Write the failing tests**

```kotlin
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
            phonePortrait.reduce(FullscreenInput.Toggle),
        )
    }

    @Test
    fun enteringOnALargeWindowOnlyGoesImmersive() {
        assertEquals(
            FullscreenState(isFullscreen = true, orientationLock = OrientationLock.NONE, window = LARGE),
            FullscreenState().after(window(LARGE), FullscreenInput.Toggle),
        )
    }

    @Test
    fun enteringBeforeTheWindowIsKnownDoesNotRotate() {
        assertEquals(OrientationLock.NONE, FullscreenState().reduce(FullscreenInput.Toggle).orientationLock)
    }

    @Test
    fun exitingOnAPhoneInLandscapeLocksPortrait() {
        val exited = phoneLandscape.reduce(FullscreenInput.Toggle)

        assertFalse(exited.isFullscreen)
        assertEquals(OrientationLock.PORTRAIT, exited.orientationLock)
    }

    @Test
    fun backExitsLikeTheButton() {
        assertEquals(phoneLandscape.reduce(FullscreenInput.Toggle), phoneLandscape.reduce(FullscreenInput.Exit))
    }

    @Test
    fun exitingOnALargeWindowLocksNothing() {
        val exited = FullscreenState().after(window(LARGE), FullscreenInput.Toggle, FullscreenInput.Exit)

        assertEquals(FullscreenState(isFullscreen = false, orientationLock = OrientationLock.NONE, window = LARGE), exited)
    }

    @Test
    fun exitWhenInlineChangesNothing() {
        assertEquals(phonePortrait, phonePortrait.reduce(FullscreenInput.Exit))
    }

    @Test
    fun theLockReleasesOnceThePhoneIsHeldThatWay() {
        val entered = phonePortrait.after(FullscreenInput.Toggle, window(PHONE_LANDSCAPE))

        val released = entered.reduce(held(LANDSCAPE))

        assertEquals(OrientationLock.NONE, released.orientationLock)
        assertTrue(released.isFullscreen)
    }

    @Test
    fun theLockHoldsWhileThePhoneIsStillUpright() {
        val entered = phonePortrait.after(FullscreenInput.Toggle, window(PHONE_LANDSCAPE))

        assertEquals(entered, entered.reduce(held(PORTRAIT)))
    }

    @Test
    fun aFlatPhoneNeverReleasesTheLock() {
        val entered = phonePortrait.after(FullscreenInput.Toggle, window(PHONE_LANDSCAPE))

        assertEquals(entered, entered.reduce(held(UNKNOWN)))
    }

    @Test
    fun theLockHoldsWithAutoRotateOff() {
        val entered = phonePortrait.after(FullscreenInput.Toggle, window(PHONE_LANDSCAPE))
        val exited = entered.reduce(FullscreenInput.Toggle)

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
        val entered = phonePortrait.reduce(FullscreenInput.Toggle)

        // The window can report portrait again before the requested rotation lands.
        val stillEntered = entered.reduce(window(PHONE_PORTRAIT))

        assertTrue(stillEntered.isFullscreen)
        assertEquals(OrientationLock.LANDSCAPE, stillEntered.orientationLock)
    }

    @Test
    fun aLargeWindowKeepsTheUsersChoiceWhenItRotates() {
        val entered = FullscreenState().after(window(LARGE), FullscreenInput.Toggle)

        assertTrue(entered.reduce(window(LARGE)).isFullscreen)
        assertFalse(FullscreenState().after(window(LARGE), window(LARGE)).isFullscreen)
    }

    @Test
    fun buttonThenTurningThePhoneThenUprightEndsInlineAndUnlocked() {
        val state = phonePortrait.after(
            FullscreenInput.Toggle,
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
        // After Back in landscape the portrait lock is pending: stay inline even in a short window.
        assertFalse(phoneLandscape.reduce(FullscreenInput.Exit).showsFullscreenIn(PHONE_LANDSCAPE))
        assertTrue(phonePortrait.reduce(FullscreenInput.Toggle).showsFullscreenIn(PHONE_PORTRAIT))
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest --tests "com.shafayatb.streamly.player.FullscreenRuleTest"`
Expected: compilation FAILS (`Unresolved reference: FullscreenState`, `WindowShape`, …).

- [ ] **Step 3: Write the rule**

```kotlin
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
    /** The fullscreen button. */
    data object Toggle : FullscreenInput

    /** Back while fullscreen. */
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
    FullscreenInput.Toggle -> if (isFullscreen) exit() else enter()
    FullscreenInput.Exit -> if (isFullscreen) exit() else this
    is FullscreenInput.WindowChanged -> onWindow(input.shape)
    is FullscreenInput.DeviceChanged -> onDevice(input.orientation, input.autoRotate)
}

/**
 * Whether [window] shows the video fullscreen. The screen reports a new window a frame after
 * composing it, so the layout asks what the report will lead to rather than flash inline first.
 */
fun FullscreenState.showsFullscreenIn(window: WindowShape): Boolean =
    reduce(FullscreenInput.WindowChanged(window)).isFullscreen

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
```

Check `fromAxis` against the tests: 30 → 30 is not in `31 until 60` → snaps to portrait; 60 →
not in range → `(105) / 90 = 1` → landscape; 45 → in range → previous; 345 → `fromAxis` 75, not
in range → `390 / 90 % 4 = 0` → portrait; 315 → `fromAxis` 45 → previous.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest --tests "com.shafayatb.streamly.player.FullscreenRuleTest"`
Expected: PASS, 24 tests.

---

### Task 2: The ViewModel keeps fullscreen in the player state

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/PlayerState.kt`
- Modify: `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/PlayerIntent.kt`
- Modify: `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/PlayerViewModel.kt` (`onIntent`)
- Test: `shared/src/commonTest/kotlin/com/shafayatb/streamly/player/PlayerFullscreenTest.kt`

**Interfaces:**
- Consumes: Task 1's `FullscreenState`, `FullscreenInput`, `reduce`, `WindowShape`,
  `DeviceOrientation`.
- Produces:
  - `PlayerState.fullscreen: FullscreenState` (default `FullscreenState()`).
  - `PlayerIntent.ToggleFullscreen`.
  - `PlayerIntent.WindowChanged(val shape: WindowShape)`.
  - `PlayerIntent.DeviceOrientationChanged(val orientation: DeviceOrientation, val autoRotate: Boolean)`.
  - `PlayerIntent.NavigateBack` now exits fullscreen first.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.shafayatb.streamly.player

import app.cash.turbine.test
import com.shafayatb.streamly.testing.FakeDownloadRepository
import com.shafayatb.streamly.testing.FakeVideoPlayer
import com.shafayatb.streamly.testing.FakeVideoRepository
import com.shafayatb.streamly.testing.FakeWatchHistoryRepository
import com.shafayatb.streamly.testing.testVideo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerFullscreenTest {

    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-01T00:00:00Z")
    }
    private val player = FakeVideoPlayer()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PlayerViewModel(
        videoId = "v1",
        videoRepository = FakeVideoRepository(videos = listOf(testVideo(id = "v1", duration = 10.minutes))),
        videoPlayer = player,
        downloadRepository = FakeDownloadRepository().apply { emit() },
        watchHistory = FakeWatchHistoryRepository(),
        clock = clock,
    )

    @Test
    fun startsInlineWithNoLock() {
        assertEquals(FullscreenState(), viewModel().state.value.fullscreen)
    }

    @Test
    fun theButtonOnAPhoneInPortraitLocksLandscape() {
        val viewModel = viewModel()
        viewModel.onIntent(PlayerIntent.WindowChanged(WindowShape.PHONE_PORTRAIT))

        viewModel.onIntent(PlayerIntent.ToggleFullscreen)

        val fullscreen = viewModel.state.value.fullscreen
        assertTrue(fullscreen.isFullscreen)
        assertEquals(OrientationLock.LANDSCAPE, fullscreen.orientationLock)
    }

    @Test
    fun holdingThePhoneSidewaysReleasesTheLock() {
        val viewModel = viewModel()
        viewModel.onIntent(PlayerIntent.WindowChanged(WindowShape.PHONE_PORTRAIT))
        viewModel.onIntent(PlayerIntent.ToggleFullscreen)

        viewModel.onIntent(PlayerIntent.DeviceOrientationChanged(DeviceOrientation.LANDSCAPE, autoRotate = true))

        assertEquals(OrientationLock.NONE, viewModel.state.value.fullscreen.orientationLock)
    }

    @Test
    fun backInFullscreenExitsInsteadOfLeaving() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(PlayerIntent.WindowChanged(WindowShape.PHONE_LANDSCAPE))
        assertTrue(viewModel.state.value.fullscreen.isFullscreen)

        viewModel.events.test {
            viewModel.onIntent(PlayerIntent.NavigateBack)
            expectNoEvents()
            assertFalse(viewModel.state.value.fullscreen.isFullscreen)
            assertEquals(OrientationLock.PORTRAIT, viewModel.state.value.fullscreen.orientationLock)

            viewModel.onIntent(PlayerIntent.NavigateBack)
            assertEquals(PlayerEvent.NavigateBack, awaitItem())
        }
    }

    @Test
    fun fullscreenNeverTouchesPlayback() {
        val viewModel = viewModel()
        val before = player.state.value

        viewModel.onIntent(PlayerIntent.WindowChanged(WindowShape.PHONE_PORTRAIT))
        viewModel.onIntent(PlayerIntent.ToggleFullscreen)
        viewModel.onIntent(PlayerIntent.WindowChanged(WindowShape.PHONE_LANDSCAPE))
        viewModel.onIntent(PlayerIntent.ToggleFullscreen)

        assertEquals(before, player.state.value)
        assertEquals(listOf("v1"), player.loadedVideoIds)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest --tests "com.shafayatb.streamly.player.PlayerFullscreenTest"`
Expected: compilation FAILS (`Unresolved reference: fullscreen`, `ToggleFullscreen`, `WindowChanged`, …).

- [ ] **Step 3: Add the state field and the intents**

In `PlayerState.kt`, add the last field:

```kotlin
    val isRemoveDownloadDialogShown: Boolean = false,
    val fullscreen: FullscreenState = FullscreenState(),
)
```

In `PlayerIntent.kt`, after `NavigateBack`:

```kotlin
    /** Leaves fullscreen when it shows, and the screen otherwise. */
    data object NavigateBack : PlayerIntent

    data object ToggleFullscreen : PlayerIntent

    /** The window's shape, reported on first composition and whenever it changes. */
    data class WindowChanged(val shape: WindowShape) : PlayerIntent

    /** How the phone is held, reported only while an orientation lock is held. */
    data class DeviceOrientationChanged(val orientation: DeviceOrientation, val autoRotate: Boolean) : PlayerIntent
```

- [ ] **Step 4: Fold them in `PlayerViewModel.onIntent`**

Replace the `NavigateBack` branch and add three branches:

```kotlin
            PlayerIntent.NavigateBack -> if (screen.value.fullscreen.isFullscreen) {
                updateFullscreen(FullscreenInput.Exit)
            } else {
                send(PlayerEvent.NavigateBack)
            }
            PlayerIntent.ToggleFullscreen -> updateFullscreen(FullscreenInput.Toggle)
            is PlayerIntent.WindowChanged -> updateFullscreen(FullscreenInput.WindowChanged(intent.shape))
            is PlayerIntent.DeviceOrientationChanged ->
                updateFullscreen(FullscreenInput.DeviceChanged(intent.orientation, intent.autoRotate))
```

and the helper next to `togglePlayPause`:

```kotlin
    private fun updateFullscreen(input: FullscreenInput) {
        screen.update { it.copy(fullscreen = it.fullscreen.reduce(input)) }
    }
```

The `state` `combine` already copies `screen`, so `fullscreen` flows through unchanged.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `rtk proxy ./gradlew :shared:testAndroidHostTest`
Expected: PASS. The new 5 tests pass, and the existing `PlayerViewModelTest` back-navigation
tests still pass, because a fresh ViewModel is inline.

---

### Task 3: The fullscreen button and Back

**Files:**
- Create: `shared/src/commonMain/composeResources/drawable/ic_fullscreen.xml`, `ic_fullscreen_exit.xml`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml` (after `cd_unmute`)
- Modify: `gradle/libs.versions.toml`, `shared/build.gradle.kts`
- Modify: `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/PlayerViewport.kt`
- Modify: `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/PlayerScreen.kt` (`playerLayout`, `PlayerScreen`, `InlinePlayer`)
- Test: `shared/src/androidDeviceTest/kotlin/com/shafayatb/streamly/player/PlayerFullscreenScreenTest.kt`

**Interfaces:**
- Consumes: `PlayerState.fullscreen`, `showsFullscreenIn`, `toWindowShape`, and
  `PlayerIntent.ToggleFullscreen` / `NavigateBack`.
- Produces: `PlayerViewport(state, onIntent, isFullscreen: Boolean, modifier)`.

- [ ] **Step 1: Write the failing device tests**

```kotlin
package com.shafayatb.streamly.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.UiText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

// Run with the device in portrait: the inline case expects a compact-width window.
class PlayerFullscreenScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val intents = mutableListOf<PlayerIntent>()

    private val details = VideoDetailsUi(
        id = "v1",
        title = "Big Buck Bunny",
        channelName = "Blender Studio",
        description = "",
        thumbnailUrl = "",
        views = UiText.DynamicString("1M views"),
        age = UiText.DynamicString("1 day ago"),
        isLive = false,
    )

    private val enteredOnAPhone = FullscreenState(
        isFullscreen = true,
        orientationLock = OrientationLock.LANDSCAPE,
        window = WindowShape.PHONE_PORTRAIT,
    )

    private fun showPlayer(fullscreen: FullscreenState) {
        rule.setContent {
            // Inspection mode skips the video surface, which needs the app's Koin graph.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                StreamlyTheme {
                    PlayerScreen(
                        state = PlayerState(content = PlayerContent.Loaded(details), fullscreen = fullscreen),
                        onIntent = { intents += it },
                    )
                }
            }
        }
    }

    @Test
    fun theInlinePlayerOffersFullScreen() {
        showPlayer(FullscreenState(window = WindowShape.PHONE_PORTRAIT))

        rule.onNodeWithText("Big Buck Bunny").assertExists()
        rule.onNodeWithContentDescription("Full screen").performClick()

        assertEquals(listOf<PlayerIntent>(PlayerIntent.ToggleFullscreen), intents)
    }

    @Test
    fun fullscreenHidesTheDetailsAndOffersExit() {
        showPlayer(enteredOnAPhone)

        rule.onNodeWithText("Big Buck Bunny").assertDoesNotExist()
        rule.onNodeWithContentDescription("Exit full screen").performClick()

        assertEquals(listOf<PlayerIntent>(PlayerIntent.ToggleFullscreen), intents)
    }

    @Test
    fun backInFullscreenGoesToTheViewModel() {
        showPlayer(enteredOnAPhone)

        // Not intercepted, this Back would finish the activity and fail the test.
        Espresso.pressBack()
        rule.waitForIdle()

        assertEquals(listOf<PlayerIntent>(PlayerIntent.NavigateBack), intents)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `rtk proxy ./gradlew :shared:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=com.shafayatb.streamly.player.PlayerFullscreenScreenTest`
(with `ANDROID_SERIAL=emulator-5554`, the emulator in portrait)
Expected: compilation passes, since Task 2 added `fullscreen`. All 3 tests FAIL: there is no
"Full screen" node, the details still show, and Back finishes the activity.

- [ ] **Step 3: Add the icons and strings**

`ic_fullscreen.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M7,14L5,14v5h5v-2L7,17v-3zM5,10h2L7,7h3L10,5L5,5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2L19,5h-5z" />
</vector>
```

`ic_fullscreen_exit.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M5,16h3v3h2v-5L5,14v2zM8,8L5,8v2h5L10,5L8,5v3zM14,19h2v-3h3v-2h-5v5zM16,8L16,5h-2v5h5L19,8h-3z" />
</vector>
```

`strings.xml`, after `cd_unmute`:

```xml
    <string name="cd_enter_fullscreen">Full screen</string>
    <string name="cd_exit_fullscreen">Exit full screen</string>
```

- [ ] **Step 4: Declare navigationevent-compose**

`gradle/libs.versions.toml`: under `[versions]` add `navigationevent = "1.1.0"`, and under
`[libraries]` add:

```toml
navigationevent-compose = { module = "org.jetbrains.androidx.navigationevent:navigationevent-compose", version.ref = "navigationevent" }
```

`shared/build.gradle.kts`, `commonMain.dependencies`, after `libs.navigation3.ui`:

```kotlin
            implementation(libs.navigationevent.compose)
```

Then confirm that the resolved version did not move:
`rtk proxy ./gradlew :shared:dependencies --configuration androidRuntimeClasspath | grep org.jetbrains.androidx.navigationevent`
should show `1.1.0` with no `->` upgrade.

- [ ] **Step 5: Add the toggle to `PlayerViewport`**

Give `PlayerViewport` an `isFullscreen: Boolean` parameter before `modifier`, and pass it to
`ControlsOverlay` the same way:

```kotlin
internal fun PlayerViewport(
    state: PlayerState,
    onIntent: (PlayerIntent) -> Unit,
    isFullscreen: Boolean,
    modifier: Modifier = Modifier,
)
```

```kotlin
                ControlsOverlay(
                    playback = playback,
                    isFullscreen = isFullscreen,
                    showPlayPause = !showSpinner,
                    …
```

In `ControlsOverlay` (new parameter `isFullscreen: Boolean` after `playback`), after the mute
button:

```kotlin
                OverlayIconButton(
                    icon = if (isFullscreen) Res.drawable.ic_fullscreen_exit else Res.drawable.ic_fullscreen,
                    contentDescription = if (isFullscreen) Res.string.cd_exit_fullscreen else Res.string.cd_enter_fullscreen,
                    onClick = { onIntent(PlayerIntent.ToggleFullscreen) },
                )
```

Add the imports `ic_fullscreen`, `ic_fullscreen_exit`, `cd_enter_fullscreen`, and
`cd_exit_fullscreen`.

- [ ] **Step 6: The layout follows the state, and Back is handled while fullscreen**

In `PlayerScreen.kt`, replace `playerLayout()`:

```kotlin
@Composable
private fun playerLayout(fullscreen: FullscreenState): PlayerLayout {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return when {
        fullscreen.showsFullscreenIn(windowSizeClass.toWindowShape()) -> PlayerLayout.FULL_SCREEN
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            PlayerLayout.TWO_PANE
        else -> PlayerLayout.SINGLE_COLUMN
    }
}
```

Update the `FULL_SCREEN` KDoc: "The user chose fullscreen, or a phone was turned sideways: the
video fills the window." In `PlayerScreen`, compute the layout once, register the Back handler,
and pass `isFullscreen`:

```kotlin
    val layout = playerLayout(state.fullscreen)
    // Registered inside NavDisplay's content, so it takes Back before NavDisplay does.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = layout == PlayerLayout.FULL_SCREEN,
        onBackCompleted = { onIntent(PlayerIntent.NavigateBack) },
    )
    Box(…) {
        when (layout) {
            PlayerLayout.FULL_SCREEN -> {
                ImmersiveModeEffect()
                PlayerViewport(state = state, onIntent = onIntent, isFullscreen = true, modifier = Modifier.fillMaxSize())
            }
```

In `InlinePlayer`, pass `isFullscreen = false`. The imports are
`androidx.navigationevent.NavigationEventInfo`,
`androidx.navigationevent.compose.NavigationBackHandler`, and
`androidx.navigationevent.compose.rememberNavigationEventState`.

The overlay arrow already sends `PlayerIntent.NavigateBack`, which now exits fullscreen first
(Task 2), so it needs no change.

- [ ] **Step 7: Run the tests to verify they pass**

Run the device test command from Step 2: PASS, 3 tests. Then run
`rtk proxy ./gradlew :shared:connectedAndroidDeviceTest` (217 expected: the 19 Compose tests plus
the 198 common tests, which also run on the device) and
`rtk proxy ./gradlew :shared:testAndroidHostTest`. Both should PASS.

---

### Task 4: Lock the orientation while fullscreen

The rule's logic is pinned by Task 1. This task is Android glue only: one Activity call, and a
sensor listener with no branching beyond what Task 1 tests. The host suite cannot run it, so the
device script (Task 5) is its test. Write it last, and verify it on both devices.

**Files:**
- Create: `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/FullscreenPlatform.kt`
- Create: `shared/src/androidMain/kotlin/com/shafayatb/streamly/player/FullscreenPlatform.android.kt`
- Modify: `shared/src/androidMain/kotlin/com/shafayatb/streamly/core/presentation/WindowEffects.android.kt` (`findActivity` → `internal`)
- Modify: `shared/src/commonMain/kotlin/com/shafayatb/streamly/player/PlayerScreen.kt` (`PlayerRoot`)

**Interfaces:**
- Consumes: `OrientationLock`, `DeviceOrientation`, `deviceOrientationOf`, `toWindowShape`,
  `PlayerIntent.WindowChanged` / `DeviceOrientationChanged`.
- Produces: `OrientationLockEffect(lock: OrientationLock)` and
  `DeviceOrientationEffect(lock: OrientationLock, onChange: (DeviceOrientation, autoRotate: Boolean) -> Unit)`.

- [ ] **Step 1: The expect declarations**

```kotlin
package com.shafayatb.streamly.player

import androidx.compose.runtime.Composable

/**
 * Asks the system for [lock]'s orientation while the Player is composed, and hands the
 * orientation back when the Player leaves, though not when a rotation recreates the activity.
 */
@Composable
expect fun OrientationLockEffect(lock: OrientationLock)

/**
 * While [lock] is held, reports how the phone is held and whether auto-rotate is on, each time
 * either changes and once when the lock changes. Off while no lock is held.
 */
@Composable
expect fun DeviceOrientationEffect(lock: OrientationLock, onChange: (DeviceOrientation, autoRotate: Boolean) -> Unit)
```

- [ ] **Step 2: The Android actuals**

In `WindowEffects.android.kt`, change `private tailrec fun Context.findActivity()` to `internal`.

```kotlin
package com.shafayatb.streamly.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.shafayatb.streamly.core.presentation.findActivity

@Composable
actual fun OrientationLockEffect(lock: OrientationLock) {
    val activity = LocalContext.current.findActivity() ?: return
    // Set, never reset in between: handing the system UNSPECIFIED on the way from one lock to
    // the next could rotate the screen twice.
    LaunchedEffect(activity, lock) {
        activity.requestedOrientation = when (lock) {
            OrientationLock.NONE -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }
    DisposableEffect(activity) {
        onDispose {
            // The rotation this lock caused recreates the activity; the next composition applies
            // the lock again from the ViewModel, so only a real exit hands the orientation back.
            if (!activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}

@Composable
actual fun DeviceOrientationEffect(lock: OrientationLock, onChange: (DeviceOrientation, autoRotate: Boolean) -> Unit) {
    val context = LocalContext.current
    val currentOnChange by rememberUpdatedState(onChange)
    DisposableEffect(context, lock) {
        if (lock == OrientationLock.NONE) return@DisposableEffect onDispose {}
        var orientation = DeviceOrientation.UNKNOWN
        var autoRotate: Boolean? = null
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(degrees: Int) {
                val nextOrientation = deviceOrientationOf(degrees, orientation)
                val nextAutoRotate = isAutoRotateOn(context)
                if (nextOrientation == orientation && nextAutoRotate == autoRotate) return
                orientation = nextOrientation
                autoRotate = nextAutoRotate
                currentOnChange(nextOrientation, nextAutoRotate)
            }
        }
        // Without an accelerometer nothing is reported, and the lock lasts until the user exits.
        listener.enable()
        onDispose { listener.disable() }
    }
}

private fun isAutoRotateOn(context: Context): Boolean =
    Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
```

`autoRotate` starts as `null`, so the first reading after each lock change is always reported.
An exit from a held landscape lock (`LANDSCAPE` → `PORTRAIT`) restarts the effect, so it gets a
fresh reading.

- [ ] **Step 3: Wire them in `PlayerRoot`**

After `ScreenVisibilityEffect(…)`:

```kotlin
    val windowShape = currentWindowAdaptiveInfo().windowSizeClass.toWindowShape()
    LaunchedEffect(windowShape) { viewModel.onIntent(PlayerIntent.WindowChanged(windowShape)) }
    OrientationLockEffect(state.fullscreen.orientationLock)
    DeviceOrientationEffect(state.fullscreen.orientationLock) { orientation, autoRotate ->
        viewModel.onIntent(PlayerIntent.DeviceOrientationChanged(orientation, autoRotate))
    }
```

These live in the Root, not in the stateless `PlayerScreen`, so the Compose tests never rotate
their activity.

- [ ] **Step 4: Build and run everything automated**

Run: `rtk proxy ./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest check`
Expected: BUILD SUCCESSFUL. Host tests: 298 + 24 + 5 = **327**. Lint: 0 errors.

---

### Task 5: Verify on devices, review, and document

**Files:** `README.md`, `docs/agent-log.md`, `FRESH_PROMPT.md`.

- [ ] **Step 1: Install on both devices**

`rtk proxy ./gradlew :androidApp:installDebug` with each `ANDROID_SERIAL` (`emulator-5554`,
`R58T90FB67Y`). Keep `adb logcat -s StreamlyPlayer` and
`adb logcat -b events -s wm_relaunch_resume_activity` running per device. Check the orientation
with `adb shell dumpsys window | grep -E "mCurrentRotation|mRotation="`. Take screenshots with
`adb exec-out screencap -p`.

- [ ] **Step 2: The device script.** Record the device, steps, and result for each step.

**Rotation locked (emulator: `accelerometer_rotation 0`, `user_rotation 0`; A04: set
`accelerometer_rotation 0` for this block and restore 1 afterwards)**

- **R1:** Open Big Buck Bunny in portrait and tap the controls, then Full screen.
  - Expected: landscape, immersive, with an "Exit full screen" button.
  - Record a `screenrecord` of the toggle and check the contact sheet for an inline flash after
    the rotation.
- **L1:** During R1, the audio and position continue.
  - Expected: `StreamlyPlayer` shows no new `loadingStarted` for the media item, and the position
    on the overlay is continuous.
- **L2:** During R1, `wm_relaunch_resume_activity` logs exactly **one** line per toggle.
- **R2:** Tap "Exit full screen".
  - Expected: portrait, with the details and up next visible, playing throughout.
- **B1:** Enter fullscreen, then press system Back.
  - Expected: portrait inline on the Player. Back again returns to Home.
- **B2:** Enter fullscreen, then tap the overlay arrow.
  - Expected: the same as B1.
- **B3:** Inline, press Back.
  - Expected: Home at once, with no fullscreen step.
- **R3:** Enter fullscreen, press Back twice to reach Home, then check the rotation.
  - Expected: Home is in portrait and `user_rotation 1` rotates it, so the orientation was
    released.
- **R4:** Enter fullscreen, open the app switcher, return, then exit.
  - Expected: still fullscreen on return, and the video paused/resumed as the lifecycle table
    says.
- **L3:** After R1–R4, `adb shell am dumpheap -g com.shafayatb.streamly /data/local/tmp/fs.hprof`
  plus `dumpsys meminfo com.shafayatb.streamly | grep Activities`.
  - Expected: `Activities: 1`.

**Auto-rotate on (emulator: `accelerometer_rotation 1`, sensors via `adb emu sensor set
acceleration`; portrait = `0:9.81:0`, landscape = `9.81:0:0`)**

- **S1:** Portrait, tap Full screen.
  - Expected: landscape, and the lock is held while the sensor says portrait.
- **S2:** Set the sensor to landscape.
  - Expected: no visible change; the lock is released (`dumpsys`, or S3 proves it).
- **S3:** Set the sensor to portrait.
  - Expected: portrait inline, so fullscreen exited by turning the phone.
- **S4:** Set the sensor to landscape.
  - Expected: fullscreen by rotation, as today.
- **S5:** Press Back.
  - Expected: portrait inline on the Player while the sensor still says landscape (the lock
    holds).
- **S6:** Set the sensor to portrait, then landscape.
  - Expected: fullscreen again, so the lock was released once the phone was upright.

The A04 needs a hand to turn it. Ask the user to run S1–S6 on the A04 if they can; otherwise
record S1–S6 as emulator-verified only.

**Tablet (emulator `wm size 2400x1800`, `wm density 320`; then `1400x2400` at 320)**

- **T1:** Landscape tablet, Full screen.
  - Expected: immersive, no rotation, video fills the screen.
  - Back exits to the two-pane layout; Back again goes Home.
- **T2:** Portrait tablet (700 dp wide), Full screen.
  - Expected: immersive with a letterboxed video and no rotation.
  - Exit returns to the single-column layout.
- **Restore:** `wm size reset`, `wm density reset`, `user_rotation 0`.

**Regression smoke**

- **X1:** A live video shows the toggle with no seek bar, and the toggle works.
- **X2:** Shorts still autoplay, with immersive mode unchanged.
- **X3:** TalkBack labels: a `uiautomator dump` shows `content-desc="Full screen"` / `"Exit full
  screen"`.
- **X4:** Dark mode (`cmd uimode night yes`): the controls are unchanged.

- [ ] **Step 3: Fresh review**

Dispatch one reviewer on the most capable model over `git diff develop...HEAD` plus the staged
changes, with this plan's Review Focus as the checklist. Triage the findings with the
receiving-code-review skill. Fix the agreed ones test-first; record the deferred ones.

- [ ] **Step 4: Docs**

- **README:**
  - **Lifecycle table:** a row for "Fullscreen button (phone)": the activity is recreated in
    landscape, nothing is paused, and the video does not rebuffer.
  - **Adaptive layout:** describe the button, the sensor-aware lock, and tablets going immersive
    only.
  - **Known gaps:** replace the "Fullscreen follows the window" item with the multi-window edge.
    In split-screen or freeform windows Android ignores orientation requests, so the exit button
    can't rotate a short window.
  - **Further gaps:**
    - Process death while fullscreen reopens the Player inline.
    - The physical-orientation reading assumes a portrait-natural phone.
- **`docs/agent-log.md`:** task 11 with the prompt, how we worked, the decisions and why
  (including the refinement above), the problems found, the verification evidence, and the
  commits.
- **`FRESH_PROMPT.md`:** fullscreen done (item 4 struck), next is the release gate as task 12,
  updated test counts and device state.

- [ ] **Step 5: Stage and stop**

Stage everything, show `rtk proxy git status` and a `git diff --cached --stat` summary, propose
the commits below, and stop for approval.

## Commit sequence (after the user approves)

Each commit builds on its own. Verify each tree with the temporary-index and scratch-worktree
method in `FRESH_PROMPT.md`: `:androidApp:assembleDebug testAndroidHostTest`.

1. `docs: plan the player fullscreen button`: this file.
2. `feat(player): add the fullscreen rule`: `Fullscreen.kt` and `FullscreenRuleTest.kt`.
3. `feat(player): keep fullscreen in the player state`: `PlayerState`, `PlayerIntent`,
   `PlayerViewModel`, and `PlayerFullscreenTest.kt`.
4. `feat(player): add the fullscreen button`: icons, strings, catalog entry, `PlayerViewport`,
   `PlayerScreen` layout and Back handler, and `PlayerFullscreenScreenTest.kt`.
5. `feat(player): lock the orientation while fullscreen`: the expect/actual effects,
   `findActivity`, and the `PlayerRoot` wiring.
6. `docs: document the fullscreen button`: README and agent log. `FRESH_PROMPT.md` is gitignored
   and stays local.

Then merge `feature/player-fullscreen` into `develop` (`--no-ff`, Git's default message) after a
separate approval, check the integrated build, and keep the branch.
