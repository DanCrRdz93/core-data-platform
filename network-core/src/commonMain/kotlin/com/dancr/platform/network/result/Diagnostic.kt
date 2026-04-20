package com.dancr.platform.network.result

/**
 * Structured diagnostic information attached to a [NetworkError].
 *
 * Carries a human-readable [description], an optional root [cause], and
 * arbitrary [metadata] for logging and debugging. Never shown to end users.
 *
 * **Example:**
 * ```kotlin
 * val diagnostic = Diagnostic(
 *     description = "HTTP 503 Service Unavailable",
 *     cause = originalException,
 *     metadata = mapOf("statusCode" to "503", "retryAfter" to "30")
 * )
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
