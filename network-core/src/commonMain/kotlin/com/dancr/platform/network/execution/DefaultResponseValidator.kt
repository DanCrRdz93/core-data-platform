package com.dancr.platform.network.execution

import com.dancr.platform.network.client.RawResponse

/**
 * Built-in [ResponseValidator] that considers HTTP 2xx status codes as valid.
 *
 * Any non-2xx response is returned as [ValidationOutcome.Invalid] with the
 * status code included for downstream error classification.
 *
 * **Example:**
 * ```kotlin
 * val validator = DefaultResponseValidator()
 *
 * validator.validate(RawResponse(statusCode = 200)) // Valid
 * validator.validate(RawResponse(statusCode = 404)) // Invalid("HTTP 404", 404)
 * ```
 *
 * @see ResponseValidator
 */
class DefaultResponseValidator : ResponseValidator {

    override fun validate(response: RawResponse): ValidationOutcome {
        return if (response.isSuccessful) {
            ValidationOutcome.Valid
        } else {
            ValidationOutcome.Invalid(
                reason = "HTTP ${response.statusCode}",
                statusCode = response.statusCode
            )
        }
    }
}
