package com.dancr.platform.network.execution

import com.dancr.platform.network.client.RawResponse

/**
 * Validates a [RawResponse] after transport but before deserialization.
 *
 * The default implementation ([DefaultResponseValidator]) checks for 2xx status codes.
 * Override for custom semantics (e.g. treat 404 as valid for "not found" use cases).
 *
 * **Example — custom validator that accepts 404:**
 * ```kotlin
 * class NotFoundAwareValidator : ResponseValidator {
 *     override fun validate(response: RawResponse): ValidationOutcome =
 *         if (response.statusCode in 200..299 || response.statusCode == 404)
 *             ValidationOutcome.Valid
 *         else
 *             ValidationOutcome.Invalid(
 *                 reason = "HTTP ${response.statusCode}",
 *                 statusCode = response.statusCode
 *             )
 * }
 * ```
 *
 * @see DefaultResponseValidator for the built-in implementation.
 * @see ValidationOutcome for the result type.
 */
interface ResponseValidator {

    /**
     * Validates the given [response].
     *
     * @param response The raw HTTP response to validate.
     * @return [ValidationOutcome.Valid] or [ValidationOutcome.Invalid] with a reason.
     */
    fun validate(response: RawResponse): ValidationOutcome
}

/**
 * Result of [ResponseValidator.validate].
 *
 * @see ResponseValidator
 */
sealed class ValidationOutcome {

    /** The response is considered valid and may proceed to deserialization. */
    data object Valid : ValidationOutcome()

    /**
     * The response is invalid.
     *
     * @property reason     Human-readable explanation.
     * @property statusCode Optional HTTP status code for error classification.
     */
    data class Invalid(
        val reason: String,
        val statusCode: Int? = null
    ) : ValidationOutcome()
}
