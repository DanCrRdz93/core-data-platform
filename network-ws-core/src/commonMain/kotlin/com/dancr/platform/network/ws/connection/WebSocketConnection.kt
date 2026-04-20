package com.dancr.platform.network.ws.connection

import com.dancr.platform.network.ws.client.WebSocketFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Managed WebSocket connection with automatic reconnection and state tracking.
 *
 * Returned by [SafeWebSocketExecutor][com.dancr.platform.network.ws.execution.SafeWebSocketExecutor]
 * — consumers never interact with the raw engine directly.
 *
 * **Lifecycle:**
 * 1. Connection starts immediately upon creation.
 * 2. Incoming frames arrive via the [incoming] Flow.
 * 3. Send frames with [send] / [sendText] / [sendBinary].
 * 4. Observe connection state via [state] StateFlow.
 * 5. Call [close] to disconnect and release resources.
 *
 * **Example:**
 * ```kotlin
 * val connection: WebSocketConnection = executor.connect(
 *     WebSocketRequest(path = "/stream")
 * )
 *
 * // Observe state
 * connection.state.collect { state ->
 *     when (state) {
 *         is WebSocketState.Connecting   -> showLoading(state.attempt)
 *         is WebSocketState.Connected    -> showConnected()
 *         is WebSocketState.Disconnected -> showDisconnected(state.error)
 *     }
 * }
 *
 * // Receive frames
 * connection.incoming.collect { frame -> processFrame(frame) }
 *
 * // Send
 * connection.sendText("{\"action\":\"subscribe\"}")
 *
 * // Disconnect
 * connection.close()
 * ```
 *
 * @see WebSocketState for the connection state model.
 * @see SafeWebSocketExecutor for how connections are created.
 */
interface WebSocketConnection {

    /** Observable connection state (connecting, connected, disconnected). */
    val state: StateFlow<WebSocketState>

    /** Flow of incoming data frames. Completes when the connection is permanently closed. */
    val incoming: Flow<WebSocketFrame>

    /** Sends an arbitrary [frame] (text, binary, or close). */
    suspend fun send(frame: WebSocketFrame)

    /** Convenience: sends a [WebSocketFrame.Text] with the given [text]. */
    suspend fun sendText(text: String)

    /** Convenience: sends a [WebSocketFrame.Binary] with the given [data]. */
    suspend fun sendBinary(data: ByteArray)

    /** Closes the connection with the given [code] and optional [reason]. */
    suspend fun close(code: Int = 1000, reason: String? = null)
}
