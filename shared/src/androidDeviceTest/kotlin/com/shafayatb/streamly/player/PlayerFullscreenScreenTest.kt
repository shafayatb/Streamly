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

    // The window is pinned, so these run the same on any device and in either orientation.
    private val sidewaysPhone = FullscreenState(isFullscreen = true, window = WindowShape.PHONE_LANDSCAPE)

    private fun showPlayer(fullscreen: FullscreenState, windowShape: WindowShape) {
        rule.setContent {
            // Inspection mode skips the video surface, which needs the app's Koin graph.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                StreamlyTheme {
                    PlayerScreen(
                        state = PlayerState(content = PlayerContent.Loaded(details), fullscreen = fullscreen),
                        onIntent = { intents += it },
                        windowShape = windowShape,
                    )
                }
            }
        }
    }

    @Test
    fun theInlinePlayerOffersFullScreen() {
        showPlayer(FullscreenState(window = WindowShape.PHONE_PORTRAIT), WindowShape.PHONE_PORTRAIT)

        rule.onNodeWithText("Big Buck Bunny").assertExists()
        rule.onNodeWithContentDescription("Full screen").performClick()

        assertEquals(listOf<PlayerIntent>(PlayerIntent.EnterFullscreen), intents)
    }

    @Test
    fun fullscreenHidesTheDetailsAndOffersExit() {
        showPlayer(sidewaysPhone, WindowShape.PHONE_LANDSCAPE)

        rule.onNodeWithText("Big Buck Bunny").assertDoesNotExist()
        rule.onNodeWithContentDescription("Exit full screen").performClick()

        assertEquals(listOf<PlayerIntent>(PlayerIntent.ExitFullscreen), intents)
    }

    @Test
    fun backInFullscreenGoesToTheViewModel() {
        showPlayer(sidewaysPhone, WindowShape.PHONE_LANDSCAPE)

        // Not intercepted, this Back would finish the activity and fail the test.
        Espresso.pressBack()
        rule.waitForIdle()

        assertEquals(listOf<PlayerIntent>(PlayerIntent.NavigateBack), intents)
    }
}
