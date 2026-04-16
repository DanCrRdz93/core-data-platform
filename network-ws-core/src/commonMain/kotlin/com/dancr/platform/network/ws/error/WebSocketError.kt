package com.dancr.platform.network.ws.error

import com.dancr.platform.network.ws.result.Diagnostic

// Semantic error hierarchy for WebSocket operations.
// Analogous to NetworkError for HTTP, but covering connection-specific failures.
// `message` is always safe for end users. `diagnostic` is for logging/debugging only.
sealed class WebSocketError {

    abstract val message: String
    abstract val diagnostic: Diagnostic?

    // Override in subclasses to control which errors trigger automatic reconnection.
    open val isRetryable: Boolean get() = false

    // -- Connection layer --

    // Failed to establish the initial WebSocket connection (DNS, TCP, TLS handshake).
    data class ConnectionFailed(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Unable to connect to the server"
        override val isRetryable: Boolean get() = true
    }

    // Connection was established but dropped unexpectedly (network change, server restart).
    data class ConnectionLost(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Connection lost"
        override val isRetryable: Boolean get() = true
    }

    // -- Protocol layer --

    // WebSocket protocol violation (invalid frame, unexpected opcode).
    data class ProtocolError(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Protocol error"
    }

    // Server sent a Close frame. `code` follows RFC 6455 status codes.
    data class ClosedByServer(
        val code: Int,
        val reason: String? = null,
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Connection closed by server"
        override val isRetryable: Boolean get() = code != 1000 && code != 1001
    }

    // -- Security layer --

    // HTTP 401/403 during the WebSocket handshake.
    data class Authentication(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Authentication required"
    }

    // -- Data processing layer --

    // Failed to deserialize an incoming frame.
    data class Serialization(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Failed to process message"
    }

    // -- Transport layer --

    // Connection timed out (connect timeout or missing pong).
    data class Timeout(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "Connection timed out"
        override val isRetryable: Boolean get() = true
    }

    // -- Catch-all --

    // Unclassified error.
    data class Unknown(
        override val diagnostic: Diagnostic? = null
    ) : WebSocketError() {
        override val message: String get() = "An unexpected error occurred"
    }

    override fun toString(): String =
        "${this::class.simpleName}(message=$message, isRetryable=$isRetryable)"
}
