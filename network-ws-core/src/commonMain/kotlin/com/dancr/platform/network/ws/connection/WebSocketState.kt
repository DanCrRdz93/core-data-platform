package com.dancr.platform.network.ws.connection

import com.dancr.platform.network.ws.error.WebSocketError

/**
 * Observable WebSocket connection state.
 *
 * Consumers use this to update UI (e.g. show “Reconnecting…” banner)
 * without inspecting individual frames.
 *
 * **Example:**
 * ```kotlin
 * connection.state.collect { state ->
 *     when (state) {
 *         is WebSocketState.Connecting   -> showReconnecting(state.attempt)
 *         is WebSocketState.Connected    -> hideReconnectBanner()
 *         is WebSocketState.Disconnected -> showOffline(state.error?.message)
 *     }
 * }
 * ```
 *
 * @see WebSocketConnection.state
 */
sealed class WebSocketState {

    /**
     * A connection attempt is in progress.
     *
     * @property attempt Zero-based attempt index (0 = first attempt, 1 = first reconnect, etc.).
     */
    data class Connecting(val attempt: Int = 0) : WebSocketState()

    /** The WebSocket connection is open and active. */
    data object Connected : WebSocketState()

    /**
     * The connection is closed.
     *
     * @property error The [WebSocketError] that caused the disconnection, or `null` for graceful close.
     */
    data class Disconnected(val error: WebSocketError? = null) : WebSocketState()
}
