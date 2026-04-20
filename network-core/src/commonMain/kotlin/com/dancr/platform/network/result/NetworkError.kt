package com.dancr.platform.network.result

/**
 * Sealed hierarchy of network-related errors.
 *
 * Every variant exposes a user-safe [message], optional [diagnostic] detail,
 * and an [isRetryable] flag that the retry loop in [DefaultSafeRequestExecutor][com.dancr.platform.network.execution.DefaultSafeRequestExecutor]
 * uses to decide whether to attempt again.
 *
 * **Example — pattern-matching on errors:**
 * ```kotlin
 * when (error) {
 *     is NetworkError.Connectivity      -> showOfflineBanner()
 *     is NetworkError.Timeout            -> showRetryButton()
 *     is NetworkError.Authentication     -> navigateToLogin()
 *     is NetworkError.Authorization      -> showAccessDenied()
 *     is NetworkError.ClientError        -> showBadRequest(error.statusCode)
 *     is NetworkError.ServerError        -> showServerDown()
 *     is NetworkError.Serialization      -> reportBug(error.diagnostic)
 *     is NetworkError.ResponseValidation -> showValidationError(error.reason)
 *     is NetworkError.Cancelled          -> { /* no-op */ }
 *     is NetworkError.Unknown            -> logAndIgnore(error.diagnostic)
 * }
 * ```
 *
 * @see Diagnostic for the structured diagnostic payload.
 * @see ErrorClassifier for how errors are produced.
 */
sealed class NetworkError {

    /** User-safe error message. Never contains sensitive information. */
    abstract val message: String

    /** Optional diagnostic detail for logging and debugging. */
    abstract val diagnostic: Diagnostic?

    /**
     * Whether this error should trigger automatic retry.
     * Override in subclasses or provide custom [ErrorClassifier] implementations
     * for different retryability semantics.
     */
    open val isRetryable: Boolean get() = false

    // -- Transport layer --

    /** Network connectivity failure (DNS resolution, socket connection, no route). Retryable. */
    data class Connectivity(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Unable to reach the server"
        override val isRetryable: Boolean get() = true
    }

    /** Request timed out (connect, read, or write timeout). Retryable. */
    data class Timeout(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "The request timed out"
        override val isRetryable: Boolean get() = true
    }

    /** Request was cancelled (e.g. coroutine scope cancellation). Not retryable. */
    data class Cancelled(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "The request was cancelled"
    }

    // -- HTTP semantic layer --

    /** HTTP 401 — authentication required or token expired. */
    data class Authentication(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Authentication required"
    }

    /** HTTP 403 — access denied (insufficient permissions). */
    data class Authorization(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Access denied"
    }

    /**
     * HTTP 4xx client error (excluding 401 and 403).
     *
     * @property statusCode The HTTP status code (e.g. 400, 404, 422).
     */
    data class ClientError(
        val statusCode: Int,
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Invalid request"
    }

    /**
     * HTTP 5xx server error. Retryable.
     *
     * @property statusCode The HTTP status code (e.g. 500, 502, 503).
     */
    data class ServerError(
        val statusCode: Int,
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Server error"
        override val isRetryable: Boolean get() = true
    }

    // -- Data processing layer --

    /** Deserialization / parsing failure. Not retryable (same response would fail again). */
    data class Serialization(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Failed to process response data"
    }

    /**
     * Response validation failure (e.g. domain-level validation on a 2xx response).
     *
     * @property reason Human-readable explanation of the validation failure.
     */
    data class ResponseValidation(
        val reason: String,
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "Response validation failed"
    }

    // -- Catch-all --

    /** Unclassified error. Inspect [diagnostic] for details. */
    data class Unknown(
        override val diagnostic: Diagnostic? = null
    ) : NetworkError() {
        override val message: String get() = "An unexpected error occurred"
    }
}
