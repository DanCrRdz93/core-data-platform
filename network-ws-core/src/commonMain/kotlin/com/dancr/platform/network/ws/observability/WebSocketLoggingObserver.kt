package com.dancr.platform.network.ws.observability

import com.dancr.platform.network.ws.client.WebSocketFrame
import com.dancr.platform.network.ws.client.WebSocketRequest
import com.dancr.platform.network.ws.error.WebSocketError

// Built-in observer that logs WebSocket lifecycle events.
// OWASP MASVS-PRIVACY: header values are redacted by default.
class WebSocketLoggingObserver(
    private val logger: WebSocketLogger = WebSocketLogger.NOOP,
    private val tag: String = "WebSocket",
    private val headerSanitizer: (key: String, value: String) -> String = { _, _ -> "██" }
) : WebSocketEventObserver {

    override fun onConnecting(request: WebSocketRequest, attempt: Int) {
        val suffix = if (attempt > 0) " (attempt ${attempt + 1})" else ""
        logger.log(
            WebSocketLogger.Level.DEBUG,
            tag,
            "⇌ CONNECTING ${request.path}${formatHeaders(request.headers)}$suffix"
        )
    }

    override fun onConnected(request: WebSocketRequest) {
        logger.log(WebSocketLogger.Level.INFO, tag, "⇌ CONNECTED ${request.path}")
    }

    override fun onFrameReceived(request: WebSocketRequest, frame: WebSocketFrame) {
        logger.log(WebSocketLogger.Level.DEBUG, tag, "◀ ${formatFrame(frame)} ${request.path}")
    }

    override fun onFrameSent(request: WebSocketRequest, frame: WebSocketFrame) {
        logger.log(WebSocketLogger.Level.DEBUG, tag, "▶ ${formatFrame(frame)} ${request.path}")
    }

    override fun onDisconnected(request: WebSocketRequest, error: WebSocketError?) {
        if (error != null) {
            logger.log(
                WebSocketLogger.Level.ERROR,
                tag,
                "✕ DISCONNECTED ${request.path} — ${error.message}"
            )
        } else {
            logger.log(WebSocketLogger.Level.INFO, tag, "✕ DISCONNECTED ${request.path}")
        }
    }

    override fun onReconnectScheduled(request: WebSocketRequest, attempt: Int, delayMs: Long) {
        logger.log(
            WebSocketLogger.Level.WARN,
            tag,
            "⟳ Reconnect $attempt for ${request.path} after ${delayMs}ms"
        )
    }

    private fun formatFrame(frame: WebSocketFrame): String = when (frame) {
        is WebSocketFrame.Text -> "TEXT(${frame.text.length} chars)"
        is WebSocketFrame.Binary -> "BINARY(${frame.data.size} bytes)"
        is WebSocketFrame.Close -> "CLOSE(${frame.code})"
    }

    private fun formatHeaders(headers: Map<String, String>): String {
        if (headers.isEmpty()) return ""
        val sanitized = headers.map { (k, v) -> "$k: ${headerSanitizer(k, v)}" }
        return " [${sanitized.joinToString(", ")}]"
    }
}
