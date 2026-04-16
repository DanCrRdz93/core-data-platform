package com.dancr.platform.network.ws.observability

import com.dancr.platform.network.ws.client.WebSocketFrame
import com.dancr.platform.network.ws.client.WebSocketRequest
import com.dancr.platform.network.ws.error.WebSocketError

// Extension point for WebSocket observability: metrics, tracing, diagnostics.
// Default methods are no-op so implementors only override what they need.
//
// Built-in implementations:
//   WebSocketLoggingObserver — logs lifecycle events via injectable WebSocketLogger.
interface WebSocketEventObserver {

    fun onConnecting(request: WebSocketRequest, attempt: Int) {}

    fun onConnected(request: WebSocketRequest) {}

    fun onFrameReceived(request: WebSocketRequest, frame: WebSocketFrame) {}

    fun onFrameSent(request: WebSocketRequest, frame: WebSocketFrame) {}

    fun onDisconnected(request: WebSocketRequest, error: WebSocketError?) {}

    fun onReconnectScheduled(request: WebSocketRequest, attempt: Int, delayMs: Long) {}

    companion object {
        val NOOP: WebSocketEventObserver = object : WebSocketEventObserver {}
    }
}
