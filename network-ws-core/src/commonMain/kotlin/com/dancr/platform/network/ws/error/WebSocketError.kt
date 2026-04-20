package com.dancr.platform.network.ws.error

import com.dancr.platform.network.ws.result.Diagnostic

/**
 * Sealed hierarchy of WebSocket-related errors.
 *
 * Analogous to [NetworkError][com.dancr.platform.network.result.NetworkError] for HTTP,
 * but covering connection-specific failures. Every variant exposes a user-safe [message],
 * optional [diagnostic] detail, and an [isRetryable] flag that the reconnection loop uses.
 *
 * **Example — pattern-matching on errors:**
 * ```kotlin
 * when (error) {
 *     is WebSocketError.ConnectionFailed -> showOfflineBanner()
 *     is WebSocketError.ConnectionLost   -> showReconnecting()
 *     is WebSocketError.ProtocolError    -> reportBug(error.diagnostic)
 *     is WebSocketError.ClosedByServer   -> log("Server closed: ${error.code}")
 *     is WebSocketError.Authentication   -> navigateToLogin()
 *     is WebSocketError.Serialization    -> logParseError(error.diagnostic)
 *     is WebSocketError.Timeout          -> showRetryButton()
 *     is WebSocketError.Unknown          -> logAndIgnore(error.diagnostic)
 * }
 * ```
 *
 * @see Diagnostic for the structured diagnostic payload.
 * @see WebSocketErrorClassifier for how errors are produced.
 */
sealed class WebSocketError {

    /** User-safe error message. Never contains sensitive information. */
    abstract val message: String

    /** Optional diagnostic detail for logging and debugging. */
    abstract val diagnostic: Diagnostic?

    /**
     * Whether this error should trigger automatic reconnection.
     * Override in subclasses for different reconnection semantics.
     */
    open val isRetryable: Boolean get() = false

    // -- Connection layer --

    /** Failed to establish the initial WebSocket connection (DNS, TCP, TLS handshake). Retryable. */
    data class ConnectionFailed(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Unable to connect to the server"
        override val isRetryable: Boolean get() = true
    }

    /** Connection was established but dropped unexpectedly (network change, server restart). Retryable. */
    data class ConnectionLost(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Connection lost"
        override val isRetryable: Boolean get() = true
    }

    // -- Protocol layer --

    /** WebSocket protocol violation (invalid frame, unexpected opcode). */
    data class ProtocolError(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Protocol error"
    }

    /**
     * Server sent a Close frame.
     *
     * Retryable unless `code` is 1000 (Normal Closure) or 1001 (Going Away).
     *
     * @property code   RFC 6455 close status code.
     * @property reason Optional human-readable close reason from the server.
     */
    data class ClosedByServer(
        val code: Int,
        val reason: String? = null,
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Connection closed by server"
        override val isRetryable: Boolean get() = code != 1000 && code != 1001
    }

    // -- Security layer --

    /** HTTP 401/403 during the WebSocket handshake. */
    data class Authentication(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Authentication required"
    }

    // -- Data processing layer --

    /** Failed to deserialize an incoming frame. */
    data class Serialization(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Failed to process message"
    }

    // -- Transport layer --

    /** Connection timed out (connect timeout or missing pong). Retryable. */
    data class Timeout(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Connection timed out"
        override val isRetryable: Boolean get() = true
    }

    // -- Catch-all --

    /** Unclassified error. Inspect [diagnostic] for details. */
    data class Unknown(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "An unexpected error occurred"
    }

    override fun toString(): String =
        "${this::class.simpleName}(message=$message, isRetryable=$isRetryable)"
}
