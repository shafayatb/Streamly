package com.shafayatb.streamly.profile

sealed interface ProfileIntent {
    data object OpenDownloads : ProfileIntent
    data object OpenHistory : ProfileIntent
    data object OpenSettings : ProfileIntent

    /** Sign out, or for a guest, sign in: both ask first. */
    data object RequestSignOut : ProfileIntent
    data object ConfirmSignOut : ProfileIntent
    data object DismissDialog : ProfileIntent
}
