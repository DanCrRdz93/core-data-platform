package com.dancr.platform.network.ws.execution

import com.dancr.platform.network.ws.client.WebSocketRequest
import com.dancr.platform.network.ws.connection.WebSocketConnection

/**
 * Managed WebSocket executor analogous to [SafeRequestExecutor][com.dancr.platform.network.execution.SafeRequestExecutor] for HTTP.
 *
 * Wraps a raw [WebSocketEngine] with:
 * - Request interception (auth headers, tracing context)
 * - Automatic reconnection (configurable via [ReconnectPolicy][com.dancr.platform.network.ws.config.ReconnectPolicy])
 * - Error classification (transport exceptions → [WebSocketError][com.dancr.platform.network.ws.error.WebSocketError])
 * - Observability hooks (connect, disconnect, frame, reconnect events)
 *
 * **Example:**
 * ```kotlin
 * val executor: SafeWebSocketExecutor = DefaultSafeWebSocketExecutor(
 *     engine = KtorWebSocketEngine.create(config, trustPolicy),
 *     config = webSocketConfig,
 *     interceptors = listOf(authInterceptor),
 *     observers = listOf(loggingObserver)
 * )
 *
 * val connection: WebSocketConnection = executor.connect(
 *     WebSocketRequest(path = "/stream")
 * )
 *
 * connection.state.collect { state -> updateUi(state) }
 * ```
 *
 * @see DefaultSafeWebSocketExecutor for the built-in implementation.
 * @see WebSocketConnection for the managed connection handle.
 */
interface SafeWebSocketExecutor {

    /**
     * Creates a managed [WebSocketConnection] for the given [request].
     *
     * The returned connection handles reconnection automatically based on
     * the configured [ReconnectPolicy][com.dancr.platform.network.ws.config.ReconnectPolicy].
     *
     * @param request The WebSocket connection request (path, headers, sub-protocols).
     * @return A [WebSocketConnection] that manages the WebSocket lifecycle.
     */
    fun connect(request: WebSocketRequest): WebSocketConnection
}
