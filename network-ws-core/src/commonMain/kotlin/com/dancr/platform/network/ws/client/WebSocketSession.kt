package com.dancr.platform.network.ws.client

import kotlinx.coroutines.flow.Flow

// Raw transport session returned by WebSocketEngine.
// Represents a single WebSocket connection. When the connection drops,
// the incoming Flow completes. The executor decides whether to reconnect.
interface WebSocketSession {

    val incoming: Flow<WebSocketFrame>

    suspend fun send(frame: WebSocketFrame)

    suspend fun close(code: Int = 1000, reason: String? = null)

    val isActive: Boolean
}
