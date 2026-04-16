package com.dancr.platform.network.ws.connection

import com.dancr.platform.network.ws.client.WebSocketFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// Managed WebSocket connection with automatic reconnection and state tracking.
// Returned by SafeWebSocketExecutor — consumers never interact with the raw engine.
//
// Lifecycle:
//   1. Connection starts immediately upon creation.
//   2. Incoming frames arrive via the `incoming` Flow.
//   3. Send frames with `send()` / `sendText()` / `sendBinary()`.
//   4. Observe connection state via `state` StateFlow.
//   5. Call `close()` to disconnect and release resources.
interface WebSocketConnection {

    val state: StateFlow<WebSocketState>

    val incoming: Flow<WebSocketFrame>

    suspend fun send(frame: WebSocketFrame)

    suspend fun sendText(text: String)

    suspend fun sendBinary(data: ByteArray)

    suspend fun close(code: Int = 1000, reason: String? = null)
}
