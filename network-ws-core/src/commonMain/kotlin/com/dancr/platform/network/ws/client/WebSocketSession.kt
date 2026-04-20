package com.dancr.platform.network.ws.client

import kotlinx.coroutines.flow.Flow

/**
 * Raw transport session representing a single WebSocket connection.
 *
 * When the connection drops, the [incoming] Flow completes. The executor
 * decides whether to reconnect.
 *
 * **Example — receiving and sending frames:**
 * ```kotlin
 * val session = engine.connect(WebSocketRequest(path = "/stream"))
 *
 * // Receive
 * session.incoming.collect { frame ->
 *     when (frame) {
 *         is WebSocketFrame.Text -> println(frame.text)
 *         else -> { /* handle binary/close */ }
 *     }
 * }
 *
 * // Send
 * session.send(WebSocketFrame.Text("{\"action\":\"subscribe\"}"))
 *
 * // Close gracefully
 * session.close(code = 1000, reason = "Done")
 * ```
 *
 * @see WebSocketEngine for how sessions are created.
 * @see WebSocketFrame for the frame types.
 */
interface WebSocketSession {

    /** Flow of incoming frames. Completes when the connection is closed. */
    val incoming: Flow<WebSocketFrame>

    /** Sends a frame over the connection. */
    suspend fun send(frame: WebSocketFrame)

    /** Closes the connection with the given [code] and optional [reason]. */
    suspend fun close(code: Int = 1000, reason: String? = null)

    /** `true` if the underlying connection is still open. */
    val isActive: Boolean
}
