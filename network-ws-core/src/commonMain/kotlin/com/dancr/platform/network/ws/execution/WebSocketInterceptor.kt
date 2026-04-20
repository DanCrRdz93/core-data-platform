package com.dancr.platform.network.ws.execution

import com.dancr.platform.network.ws.client.WebSocketRequest

/**
 * Intercepts WebSocket connection requests before they reach the engine.
 *
 * Use for injecting auth headers, tracing context, or protocol selection.
 * Analogous to [RequestInterceptor][com.dancr.platform.network.execution.RequestInterceptor] for HTTP.
 *
 * **Example — adding an auth header:**
 * ```kotlin
 * val authInterceptor = WebSocketInterceptor { request ->
 *     request.copy(
 *         headers = request.headers + ("Authorization" to "Bearer ${tokenProvider.get()}")
 *     )
 * }
 * ```
 *
 * @see DefaultSafeWebSocketExecutor for where interceptors are applied.
 */
fun interface WebSocketInterceptor {

    /**
     * Transforms the outgoing [request] before it is sent to the engine.
     *
     * @param request The original connection request.
     * @return A potentially modified [WebSocketRequest].
     */
    fun intercept(request: WebSocketRequest): WebSocketRequest
}
