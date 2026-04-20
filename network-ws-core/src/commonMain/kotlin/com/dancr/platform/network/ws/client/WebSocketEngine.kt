package com.dancr.platform.network.ws.client

/**
 * Core transport abstraction for WebSocket connections.
 *
 * Implement per transport library (Ktor, OkHttp, URLSession). The engine handles
 * the raw WebSocket protocol — reconnection and error classification are managed
 * by [SafeWebSocketExecutor][com.dancr.platform.network.ws.execution.SafeWebSocketExecutor].
 *
 * **Example — using the Ktor implementation:**
 * ```kotlin
 * val engine: WebSocketEngine = KtorWebSocketEngine.create(
 *     config = WebSocketConfig(baseUrl = "wss://ws.example.com"),
 *     trustPolicy = myTrustPolicy
 * )
 *
 * val session = engine.connect(
 *     WebSocketRequest(path = "/chat")
 * )
 *
 * session.incoming.collect { frame -> handleFrame(frame) }
 *
 * engine.close()
 * ```
 *
 * @see KtorWebSocketEngine for the built-in Ktor implementation.
 * @see SafeWebSocketExecutor for the managed execution layer.
 */
interface WebSocketEngine {

    /**
     * Establishes a WebSocket connection.
     *
     * @param request The connection request (path, headers, sub-protocols).
     * @return An active [WebSocketSession] for sending/receiving frames.
     * @throws Exception On transport-level connection failures.
     */
    suspend fun connect(request: WebSocketRequest): WebSocketSession

    /** Releases underlying resources (connection pool, HTTP client). */
    fun close()
}
