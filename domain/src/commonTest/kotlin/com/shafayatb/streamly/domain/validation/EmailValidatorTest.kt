package com.shafayatb.streamly.domain.validation

import com.shafayatb.streamly.domain.util.Result
import kotlin.test.Test
import kotlin.test.assertEquals

class EmailValidatorTest {

    @Test
    fun acceptsWellFormedAddresses() {
        listOf(
            "anika@streamly.app",
            "first.last+tag@mail.example.co.uk",
            "  padded@example.com  ",
        ).forEach { email ->
            assertEquals(Result.Success(Unit), EmailValidator.validate(email), email)
        }
    }

    @Test
    fun rejectsBlankInput() {
        listOf("", "   ").forEach { email ->
            assertEquals(Result.Failure(EmailValidationError.BLANK), EmailValidator.validate(email))
        }
    }

    @Test
    fun rejectsMalformedAddresses() {
        listOf(
            "anika",
            "anika@",
            "@streamly.app",
            "anika@streamly",
            "anika@streamly.",
            "an ika@streamly.app",
            "anika@@streamly.app",
            "anika@.app",
        ).forEach { email ->
            assertEquals(
                Result.Failure(EmailValidationError.INVALID_FORMAT),
                EmailValidator.validate(email),
                email,
            )
        }
    }
}
