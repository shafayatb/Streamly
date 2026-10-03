package com.shafayatb.streamly.history

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.UiText
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WatchHistoryScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val intents = mutableListOf<WatchHistoryIntent>()
    private val item = HistoryItemUi(
        videoId = "a",
        title = "Media3 in 10 minutes",
        channelName = "CodeLabs",
        thumbnailUrl = "",
        durationText = "10:00",
        isLive = false,
        progress = 0.3f,
        watched = UiText.DynamicString("2 hours ago"),
    )
    private val loaded = WatchHistoryState(content = WatchHistoryContent.Loaded(persistentListOf(item)))

    private fun show(state: WatchHistoryState) {
        rule.setContent {
            StreamlyTheme { WatchHistoryScreen(state = state, onIntent = { intents += it }) }
        }
    }

    private fun dialogButton(label: String) =
        rule.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(isDialog()))

    @Test
    fun anEmptyHistoryExplainsItselfAndOffersNoClear() {
        show(WatchHistoryState(content = WatchHistoryContent.Empty))

        rule.onNodeWithText("No watch history yet").assertExists()
        rule.onNodeWithText("Clear all").assertDoesNotExist()
    }

    @Test
    fun tappingARowOpensIt() {
        show(loaded)

        rule.onNodeWithText("Media3 in 10 minutes").performClick()

        assertEquals(listOf<WatchHistoryIntent>(WatchHistoryIntent.Open("a")), intents)
    }

    @Test
    fun theRemoveButtonRemovesThatVideo() {
        show(loaded)

        rule.onNodeWithContentDescription("Remove from watch history").performClick()

        assertEquals(listOf<WatchHistoryIntent>(WatchHistoryIntent.Remove("a")), intents)
    }

    @Test
    fun clearAllAsksAndConfirmingClears() {
        show(loaded.copy(isClearDialogShown = true))

        rule.onNodeWithText("Clear watch history?").assertExists()
        dialogButton("Clear").performClick()

        assertEquals(listOf<WatchHistoryIntent>(WatchHistoryIntent.ConfirmClear), intents)
    }
}
