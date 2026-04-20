package com.dancr.platform.network.ws.result

/**
 * Structured diagnostic information attached to a [WebSocketError][com.dancr.platform.network.ws.error.WebSocketError].
 *
 * Carries a human-readable [description], an optional root [cause], and
 * arbitrary [metadata] for logging and debugging. Never shown to end users.
 *
 * **Example:**
 * ```kotlin
 * val diagnostic = Diagnostic(
 *     description = "Connection refused on port 8080",
 *     cause = originalException,
 *     metadata = mapOf("host" to "ws.example.com", "port" to "8080")
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
