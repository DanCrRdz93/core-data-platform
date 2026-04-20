package com.dancr.platform.security.error

/**
 * Structured diagnostic information attached to a [SecurityError].
 *
 * Carries a human-readable [description], an optional root [cause], and
 * arbitrary [metadata] for logging and debugging. Never shown to end users.
 *
 * **Example:**
 * ```kotlin
 * val diagnostic = Diagnostic(
 *     description = "Token refresh failed after 3 attempts",
 *     cause = originalException,
 *     metadata = mapOf("attempts" to "3", "endpoint" to "/oauth/token")
 * )
 *
 * val error = SecurityError.TokenRefreshFailed(diagnostic = diagnostic)
 * ```
 *
 * @property description Human-readable message for logs/diagnostics.
 * @property cause       Optional root [Throwable] that triggered the error.
 * @property metadata    Arbitrary key-value pairs for additional context.
 */
data class Diagnostic(
    val description: String,
    val cause: Throwable? = null,
    val metadata: Map<String, String> = emptyMap()
)
