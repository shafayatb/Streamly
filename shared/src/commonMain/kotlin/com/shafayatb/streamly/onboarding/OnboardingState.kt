package com.shafayatb.streamly.onboarding

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.core.presentation.UiText

@Immutable
data class OnboardingState(
    val pendingMethod: SignInMethod? = null,
    val error: UiText? = null,
) {
    val isBusy: Boolean get() = pendingMethod != null
}

enum class SignInMethod {
    GOOGLE,
    GUEST,
}
