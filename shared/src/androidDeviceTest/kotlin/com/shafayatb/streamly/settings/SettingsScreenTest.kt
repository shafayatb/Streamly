package com.shafayatb.streamly.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.settings.AppSettings
import com.shafayatb.streamly.domain.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val intents = mutableListOf<SettingsIntent>()
    private val loaded = SettingsState(settings = AppSettings(), version = UiText.DynamicString("1.0 (1)"))

    private fun show(state: SettingsState) {
        rule.setContent {
            StreamlyTheme { SettingsScreen(state = state, onIntent = { intents += it }) }
        }
    }

    private fun choice(label: String) = rule.onNode(hasText(label) and isSelectable() and hasAnyAncestor(isDialog()))

    @Test
    fun theWifiSwitchSendsTheNewValue() {
        show(loaded)

        rule.onNodeWithText("Download over Wi-Fi only").assertIsOff().performClick()

        assertEquals(listOf<SettingsIntent>(SettingsIntent.SetWifiOnly(true)), intents)
    }

    @Test
    fun theThemeDialogMarksTheCurrentChoiceAndPicksAnother() {
        show(loaded.copy(settings = AppSettings(themeMode = ThemeMode.DARK), dialog = SettingsDialog.THEME))

        choice("Dark").assertIsSelected()
        choice("Light").assertIsNotSelected().performClick()

        assertEquals(listOf<SettingsIntent>(SettingsIntent.SelectTheme(ThemeMode.LIGHT)), intents)
    }

    @Test
    fun theQualityDialogOffersThreeTiersAndCancelChangesNothing() {
        show(loaded.copy(dialog = SettingsDialog.DOWNLOAD_QUALITY))

        rule.onAllNodes(isSelectable() and hasAnyAncestor(isDialog())).assertCountEquals(3)
        choice("Standard · up to 480p").assertIsSelected()
        rule.onNode(hasText("Cancel") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

        assertEquals(listOf<SettingsIntent>(SettingsIntent.DismissDialog), intents)
    }

    @Test
    fun nothingCanBeChangedBeforeTheSettingsLoad() {
        show(SettingsState(version = UiText.DynamicString("1.0 (1)")))

        rule.onNodeWithText("Download over Wi-Fi only").assertIsNotEnabled()
        rule.onNodeWithText("Theme").assertIsNotEnabled()
        rule.onNodeWithText("Download quality").assertIsNotEnabled()
    }
}
