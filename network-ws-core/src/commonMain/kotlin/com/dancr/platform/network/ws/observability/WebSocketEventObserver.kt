package com.dancr.platform.network.ws.observability

import com.dancr.platform.network.ws.client.WebSocketFrame
import com.dancr.platform.network.ws.client.WebSocketRequest
import com.dancr.platform.network.ws.error.WebSocketError

/**
 * Extension point for WebSocket observability: logging, metrics, tracing, diagnostics.
 *
 * All methods have no-op defaults so implementors only override what they need.
 *
 * **Built-in implementations:**
 * - [WebSocketLoggingObserver] — logs lifecycle events via an injectable [WebSocketLogger].
 *
 * **Example — custom observer:**
 * ```kotlin
 * class AnalyticsWsObserver : WebSocketEventObserver {
 *     override fun onConnected(request: WebSocketRequest) {
 *         analytics.track("ws_connected", mapOf("path" to request.path))
 *     }
 *     override fun onDisconnected(request: WebSocketRequest, error: WebSocketError?) {
 *         analytics.track("ws_disconnected", mapOf("error" to error?.message))
 *     }
 * }
 * ```
 *
 * @see DefaultSafeWebSocketExecutor for where observers are notified.
 */
interface WebSocketEventObserver {

    /** Called when a connection attempt starts. */
    fun onConnecting(request: WebSocketRequest, attempt: Int) {}

    /** Called when the WebSocket connection is successfully established. */
    fun onConnected(request: WebSocketRequest) {}

    /** Called when a data frame is received from the server. */
    fun onFrameReceived(request: WebSocketRequest, frame: WebSocketFrame) {}

    /** Called when a data frame is sent to the server. */
    fun onFrameSent(request: WebSocketRequest, frame: WebSocketFrame) {}

    /** Called when the connection is closed, with an optional [error]. */
    fun onDisconnected(request: WebSocketRequest, error: WebSocketError?) {}

    /** Called when a reconnection attempt is scheduled. */
    fun onReconnectScheduled(request: WebSocketRequest, attempt: Int, delayMs: Long) {}

    companion object {
        /** Silent no-op observer. */
        val NOOP: WebSocketEventObserver = object : WebSocketEventObserver {}
    }
}
