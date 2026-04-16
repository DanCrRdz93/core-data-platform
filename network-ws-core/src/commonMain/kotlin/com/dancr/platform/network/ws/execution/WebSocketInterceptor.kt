package com.dancr.platform.network.ws.execution

import com.dancr.platform.network.ws.client.WebSocketRequest

// Intercepts WebSocket connection requests before they reach the engine.
// Use for injecting auth headers, tracing context, or protocol selection.
// Analogous to RequestInterceptor for HTTP.
fun interface WebSocketInterceptor {

    fun intercept(request: WebSocketRequest): WebSocketRequest
}
