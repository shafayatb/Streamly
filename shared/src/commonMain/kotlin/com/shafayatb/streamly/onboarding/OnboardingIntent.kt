package com.shafayatb.streamly.onboarding

sealed interface OnboardingIntent {
    data object ContinueWithGoogle : OnboardingIntent
    data object SignInWithEmail : OnboardingIntent
    data object ContinueAsGuest : OnboardingIntent
}
