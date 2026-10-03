package com.shafayatb.streamly.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.UiText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DownloadButtonAccessibilityTest {

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

    private fun showPlayer(download: DownloadActionUi) {
        rule.setContent {
            // Inspection mode skips the video surface, which needs the app's Koin graph.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                StreamlyTheme {
                    PlayerScreen(
                        state = PlayerState(content = PlayerContent.Loaded(details), download = download),
                        onIntent = { intents += it },
                    )
                }
            }
        }
    }

    @Test
    fun accessibilityClickOnAnInProgressDownloadCancelsIt() {
        showPlayer(DownloadActionUi.Downloading(percent = 40))

        val button = rule.onNodeWithText("40%")
        val label = button.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label
        button.performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("Cancel download", label)
        assertEquals(listOf<PlayerIntent>(PlayerIntent.CancelDownload), intents)
    }

    @Test
    fun waitingForWifiTellsTalkBackWhatItIsWaitingFor() {
        showPlayer(DownloadActionUi.WaitingForWifi)

        // Only "Waiting" fits on the button; the state description names the wait.
        val state = rule.onNodeWithText("Waiting").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

        assertEquals("Waiting for Wi-Fi", state)
    }
}
