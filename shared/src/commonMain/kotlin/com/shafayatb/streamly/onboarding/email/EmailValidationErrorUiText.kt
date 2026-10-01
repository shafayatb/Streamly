package com.shafayatb.streamly.onboarding.email

import com.shafayatb.streamly.core.presentation.UiText
import com.shafayatb.streamly.domain.validation.EmailValidationError
import streamly.shared.generated.resources.Res
import streamly.shared.generated.resources.error_email_blank
import streamly.shared.generated.resources.error_email_invalid

fun EmailValidationError.toUiText(): UiText = when (this) {
    EmailValidationError.BLANK -> UiText.Resource(Res.string.error_email_blank)
    EmailValidationError.INVALID_FORMAT -> UiText.Resource(Res.string.error_email_invalid)
}
