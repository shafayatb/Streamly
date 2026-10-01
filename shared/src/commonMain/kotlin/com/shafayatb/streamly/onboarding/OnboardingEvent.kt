package com.shafayatb.streamly.onboarding

sealed interface OnboardingEvent {
    data object NavigateToHome : OnboardingEvent
    data object NavigateToEmailSignIn : OnboardingEvent
}
