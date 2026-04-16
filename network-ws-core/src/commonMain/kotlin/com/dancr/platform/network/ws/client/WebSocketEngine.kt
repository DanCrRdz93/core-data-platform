package com.dancr.platform.network.ws.client

// Core transport abstraction for WebSocket connections.
// Implement per transport library (Ktor, OkHttp, URLSession).
// The engine handles the raw WebSocket protocol — reconnection and error
// classification are managed by SafeWebSocketExecutor.
interface WebSocketEngine {

    suspend fun connect(request: WebSocketRequest): WebSocketSession

    fun close()
}
