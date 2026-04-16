package com.dancr.platform.network.ws.execution

import com.dancr.platform.network.ws.client.WebSocketRequest
import com.dancr.platform.network.ws.connection.WebSocketConnection

// Managed WebSocket executor. Wraps a raw WebSocketEngine with:
//   - Request interception (auth headers, tracing context)
//   - Automatic reconnection (configurable via ReconnectPolicy)
//   - Error classification (transport exceptions → WebSocketError)
//   - Observability hooks (connect, disconnect, frame, reconnect events)
//
// Analogous to SafeRequestExecutor for HTTP.
interface SafeWebSocketExecutor {

    fun connect(request: WebSocketRequest): WebSocketConnection
}
