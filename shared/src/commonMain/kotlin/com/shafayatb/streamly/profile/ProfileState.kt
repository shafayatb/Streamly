package com.shafayatb.streamly.profile

import androidx.compose.runtime.Immutable

@Immutable
data class ProfileState(
    val account: ProfileAccount = ProfileAccount.Loading,
    /** The confirmation on screen, or `null` when none is. */
    val dialog: ProfileDialog? = null,
    /** While true the dialog's buttons are disabled and another confirm is ignored. */
    val isSigningOut: Boolean = false,
)

@Immutable
sealed interface ProfileAccount {
    data object Loading : ProfileAccount
    data object Guest : ProfileAccount

    /** [initials] is up to two letters for the avatar, e.g. "AR". */
    data class SignedIn(val name: String, val email: String, val initials: String) : ProfileAccount
}

enum class ProfileDialog {
    SIGN_OUT,

    /** A guest "signs in" by leaving guest mode for onboarding. */
    LEAVE_GUEST,
}
