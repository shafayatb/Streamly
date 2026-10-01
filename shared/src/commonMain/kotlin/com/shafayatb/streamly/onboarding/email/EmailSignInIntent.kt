package com.shafayatb.streamly.onboarding.email

sealed interface EmailSignInIntent {
    data class EmailChanged(val email: String) : EmailSignInIntent
    data object Submit : EmailSignInIntent
    data object NavigateBack : EmailSignInIntent
}
