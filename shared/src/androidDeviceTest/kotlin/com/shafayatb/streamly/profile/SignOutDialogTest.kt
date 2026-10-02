package com.shafayatb.streamly.profile

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shafayatb.streamly.core.designsystem.theme.StreamlyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SignOutDialogTest {

    @get:Rule
    val rule = createComposeRule()

    private val intents = mutableListOf<ProfileIntent>()
    private val anika = ProfileAccount.SignedIn("Anika Rahman", "anika@streamly.app", "AR")

    private fun show(state: ProfileState) {
        rule.setContent {
            StreamlyTheme { ProfileScreen(state = state, onIntent = { intents += it }) }
        }
    }

    private fun dialogButton(label: String) =
        rule.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(isDialog()))

    @Test
    fun confirmingTheDialogSignsOut() {
        show(ProfileState(account = anika, dialog = ProfileDialog.SIGN_OUT))

        rule.onNodeWithText("Sign out?").assertExists()
        dialogButton("Sign out").performClick()

        assertEquals(listOf<ProfileIntent>(ProfileIntent.ConfirmSignOut), intents)
    }

    @Test
    fun cancelDismissesTheDialog() {
        show(ProfileState(account = anika, dialog = ProfileDialog.SIGN_OUT))

        dialogButton("Cancel").performClick()

        assertEquals(listOf<ProfileIntent>(ProfileIntent.DismissDialog), intents)
    }

    @Test
    fun aGuestIsAskedToLeaveGuestMode() {
        show(ProfileState(account = ProfileAccount.Guest, dialog = ProfileDialog.LEAVE_GUEST))

        rule.onNodeWithText("Leave guest mode?").assertExists()
        dialogButton("Sign in").performClick()

        assertEquals(listOf<ProfileIntent>(ProfileIntent.ConfirmSignOut), intents)
    }

    @Test
    fun theButtonsAreDisabledWhileSigningOut() {
        show(ProfileState(account = anika, dialog = ProfileDialog.SIGN_OUT, isSigningOut = true))

        dialogButton("Sign out").performClick()
        dialogButton("Cancel").performClick()

        assertEquals(emptyList<ProfileIntent>(), intents)
    }
}
