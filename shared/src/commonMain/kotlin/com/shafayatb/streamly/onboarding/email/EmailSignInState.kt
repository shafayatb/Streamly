package com.shafayatb.streamly.onboarding.email

import androidx.compose.runtime.Immutable
import com.shafayatb.streamly.core.presentation.UiText

@Immutable
data class EmailSignInState(
    val email: String = "",
    val emailError: UiText? = null,
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
)
