package com.shafayatb.streamly.onboarding.email

sealed interface EmailSignInEvent {
    data object NavigateToHome : EmailSignInEvent
    data object NavigateBack : EmailSignInEvent
}
