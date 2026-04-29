package com.dancr.platform.network.ws.ktor

import com.dancr.platform.network.ws.client.WebSocketEngine
import com.dancr.platform.network.ws.client.WebSocketFrame
import com.dancr.platform.network.ws.client.WebSocketRequest
import com.dancr.platform.network.ws.client.WebSocketSession
import com.dancr.platform.network.ws.config.WebSocketConfig
import com.dancr.platform.security.trust.TrustPolicy
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive

/**
 * [WebSocketEngine] implementation backed by a Ktor [HttpClient] with WebSocket support.
 *
 * Adapts Ktor's `DefaultWebSocketSession` to the SDK's [WebSocketSession] contract.
 * Ping/Pong frames are handled by Ktor internally — only data frames are exposed.
 *
 * **Example — creating with certificate pinning:**
 * ```kotlin
 * val engine = KtorWebSocketEngine.create(
 *     config = WebSocketConfig(url = "wss://ws.example.com"),
 *     trustPolicy = DefaultTrustPolicy(
 *         pins = mapOf("ws.example.com" to setOf(CertificatePin("sha256", "AAAA...=")))
 *     )
 * )
 *
 * val session = engine.connect(WebSocketRequest(path = "/stream"))
 * session.incoming.collect { frame -> processFrame(frame) }
 * ```
 *
 * @param client The platform-configured Ktor [HttpClient] with WebSocket plugin.
 * @see WebSocketEngine
 * @see KtorWebSocketErrorClassifier for Ktor-specific error classification.
 */
class KtorWebSocketEngine(
    private val client: HttpClient
) : WebSocketEngine {

    override suspend fun connect(request: WebSocketRequest): WebSocketSession {
        val session = client.webSocketSession(request.path) {
            request.headers.forEach { (key, value) ->
                headers.append(key, value)
            }
            request.protocols.forEach { protocol ->
                headers.append("Sec-WebSocket-Protocol", protocol)
            }
        }
        return KtorWebSocketSession(session)
    }

    override fun close() {
        client.close()
    }

    companion object {

        /**
         * Creates a [KtorWebSocketEngine] with the supplied [trustPolicy].
         *
         * **Breaking change (1.0.0):** [trustPolicy] is now non-nullable. Pass
         * [TrustPolicy.SystemDefault] for explicit no-pinning, or use the [createPinned]
         * / [createSystemDefault] factories for clearer intent at the call site.
         *
         * @param config      WebSocket configuration (URL, timeouts, ping interval).
         * @param trustPolicy TLS pinning policy. Use [TrustPolicy.SystemDefault] for no-pinning.
         * @return A configured [KtorWebSocketEngine] instance.
         */
        fun create(
            config: WebSocketConfig,
            trustPolicy: TrustPolicy,
        ): KtorWebSocketEngine {
            val client = createPlatformWebSocketClient(config, trustPolicy)
            return KtorWebSocketEngine(client)
        }

        /**
         * Creates a [KtorWebSocketEngine] with **certificate pinning** enforced.
         */
        fun createPinned(
            config: WebSocketConfig,
            trustPolicy: TrustPolicy,
        ): KtorWebSocketEngine = create(config, trustPolicy)

        /**
         * Creates a [KtorWebSocketEngine] using **system-default trust without pinning**.
         */
        fun createSystemDefault(
            config: WebSocketConfig,
        ): KtorWebSocketEngine = create(config, TrustPolicy.SystemDefault)
    }
}

/**
 * Adapts Ktor's [DefaultWebSocketSession] to the SDK's [WebSocketSession] contract.
 * Ping/Pong frames are handled by Ktor internally — only data frames are exposed.
 */
private class KtorWebSocketSession(
    private val session: DefaultWebSocketSession
) : WebSocketSession {

    override val incoming: Flow<WebSocketFrame> = session.incoming
        .receiveAsFlow()
        .mapNotNull { frame -> frame.toSdkFrame() }

    override suspend fun send(frame: WebSocketFrame) {
        when (frame) {
            is WebSocketFrame.Text -> session.send(Frame.Text(frame.text))
            is WebSocketFrame.Binary -> session.send(Frame.Binary(true, frame.data))
            is WebSocketFrame.Close -> session.close(
                CloseReason(frame.code.toShort(), frame.reason ?: "")
            )
        }
    }

    override suspend fun close(code: Int, reason: String?) {
        session.close(CloseReason(code.toShort(), reason ?: ""))
    }

    override val isActive: Boolean get() = session.isActive
}

private fun Frame.toSdkFrame(): WebSocketFrame? = when (this) {
    is Frame.Text -> WebSocketFrame.Text(readText())
    is Frame.Binary -> WebSocketFrame.Binary(data)
    is Frame.Close -> {
        val reason = readReason()
        WebSocketFrame.Close(
            code = reason?.code?.toInt() ?: 1000,
            reason = reason?.message
        )
    }
    // Ping/Pong are control frames — handled by Ktor, never exposed.
    else -> null
}

private fun Frame.Close.readReason(): CloseReason? = try {
    val data = this.data
    if (data.size >= 2) {
        val code = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val message = if (data.size > 2) data.decodeToString(2, data.size) else ""
        CloseReason(code.toShort(), message)
    } else null
} catch (_: Throwable) {
    null
}
