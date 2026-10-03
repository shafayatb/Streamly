package com.shafayatb.streamly.domain.validation

import com.shafayatb.streamly.domain.util.EmptyResult
import com.shafayatb.streamly.domain.util.Error
import com.shafayatb.streamly.domain.util.Result

public enum class EmailValidationError : Error {
    BLANK,
    INVALID_FORMAT,
}

public object EmailValidator {
    private val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    /** Validates [email] after trimming surrounding whitespace. */
    public fun validate(email: String): EmptyResult<EmailValidationError> {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> Result.Failure(EmailValidationError.BLANK)
            !emailPattern.matches(trimmed) -> Result.Failure(EmailValidationError.INVALID_FORMAT)
            else -> Result.Success(Unit)
        }
    }
}
