package com.dancr.platform.network.ws.execution

import com.dancr.platform.network.ws.client.WebSocketEngine
import com.dancr.platform.network.ws.client.WebSocketFrame
import com.dancr.platform.network.ws.client.WebSocketRequest
import com.dancr.platform.network.ws.config.ReconnectPolicy
import com.dancr.platform.network.ws.config.WebSocketConfig
import com.dancr.platform.network.ws.connection.WebSocketConnection
import com.dancr.platform.network.ws.connection.WebSocketState
import com.dancr.platform.network.ws.error.WebSocketError
import com.dancr.platform.network.ws.observability.WebSocketEventObserver
import com.dancr.platform.network.ws.result.Diagnostic
import kotlin.concurrent.Volatile
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DefaultSafeWebSocketExecutor(
    private val engine: WebSocketEngine,
    private val config: WebSocketConfig,
    private val classifier: WebSocketErrorClassifier = DefaultWebSocketErrorClassifier(),
    private val interceptors: List<WebSocketInterceptor> = emptyList(),
    private val observers: List<WebSocketEventObserver> = emptyList()
) : SafeWebSocketExecutor {

    override fun connect(request: WebSocketRequest): WebSocketConnection {
        val prepared = prepareRequest(request)
        return ManagedWebSocketConnection(
            engine = engine,
            config = config,
            request = prepared,
            classifier = classifier,
            observers = observers
        )
    }

    private fun prepareRequest(request: WebSocketRequest): WebSocketRequest {
        val merged = request.copy(
            path = buildUrl(config.url, request.path),
            headers = config.defaultHeaders + request.headers
        )
        return interceptors.fold(merged) { current, interceptor ->
            interceptor.intercept(current)
        }
    }

    private fun buildUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        val relative = path.trimStart('/')
        return if (relative.isEmpty()) base else "$base/$relative"
    }
}

// Internal managed connection with automatic reconnection.
// Uses a Channel for backpressure-aware frame delivery and a dedicated
// CoroutineScope for the connection loop.
internal class ManagedWebSocketConnection(
    private val engine: WebSocketEngine,
    private val config: WebSocketConfig,
    private val request: WebSocketRequest,
    private val classifier: WebSocketErrorClassifier,
    private val observers: List<WebSocketEventObserver>
) : WebSocketConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<WebSocketState>(WebSocketState.Disconnected())
    override val state: StateFlow<WebSocketState> = _state.asStateFlow()

    private val channel = Channel<WebSocketFrame>(Channel.BUFFERED)
    override val incoming: Flow<WebSocketFrame> = channel.receiveAsFlow()

    @Volatile
    private var currentSession: com.dancr.platform.network.ws.client.WebSocketSession? = null

    @Volatile
    private var intentionalClose = false

    init {
        scope.launch { connectionLoop() }
    }

    // -- Connection loop with reconnection --

    private suspend fun connectionLoop() {
        var attempt = 0

        while (scope.coroutineContext[Job]?.isActive == true && !intentionalClose) {
            try {
                _state.value = WebSocketState.Connecting(attempt)
                notifyObservers { it.onConnecting(request, attempt) }

                val session = engine.connect(request)
                currentSession = session
                attempt = 0
                _state.value = WebSocketState.Connected
                notifyObservers { it.onConnected(request) }

                // Collect incoming frames until session ends
                session.incoming.collect { frame ->
                    notifyObservers { it.onFrameReceived(request, frame) }
                    channel.send(frame)
                }

                // Session ended normally (server closed gracefully or clean close).
                // A normal flow completion means the transport layer finished —
                // do NOT reconnect. Only errors trigger reconnection.
                currentSession = null
                _state.value = WebSocketState.Disconnected()
                notifyObservers { it.onDisconnected(request, null) }
                break

            } catch (e: CancellationException) {
                currentSession = null
                _state.value = WebSocketState.Disconnected()
                throw e
            } catch (e: Throwable) {
                currentSession = null
                val error = classifier.classify(e)
                _state.value = WebSocketState.Disconnected(error)
                notifyObservers { it.onDisconnected(request, error) }

                if (!shouldReconnect(error, attempt)) break
            }

            // Reconnect delay
            val delayMs = computeDelay(config.reconnectPolicy, attempt)
            notifyObservers { it.onReconnectScheduled(request, attempt + 1, delayMs) }
            delay(delayMs)
            attempt++
        }

        channel.close()
    }

    // -- Send --

    override suspend fun send(frame: WebSocketFrame) {
        val session = currentSession
            ?: throw IllegalStateException("Cannot send: WebSocket is not connected")
        session.send(frame)
        notifyObservers { it.onFrameSent(request, frame) }
    }

    override suspend fun sendText(text: String) = send(WebSocketFrame.Text(text))

    override suspend fun sendBinary(data: ByteArray) = send(WebSocketFrame.Binary(data))

    // -- Close --

    override suspend fun close(code: Int, reason: String?) {
        intentionalClose = true
        try {
            currentSession?.close(code, reason)
        } catch (_: Throwable) {
            // Best effort — connection may already be closed
        }
        currentSession = null
        _state.value = WebSocketState.Disconnected()
        channel.close()
        scope.cancel()
    }

    // -- Reconnection logic --

    private fun shouldReconnect(error: WebSocketError?, attempt: Int): Boolean {
        if (intentionalClose) return false

        val policy = config.reconnectPolicy
        if (policy is ReconnectPolicy.None) return false

        val maxAttempts = when (policy) {
            is ReconnectPolicy.None -> 0
            is ReconnectPolicy.FixedDelay -> policy.maxAttempts
            is ReconnectPolicy.ExponentialBackoff -> policy.maxAttempts
        }

        if (attempt >= maxAttempts) return false

        // Only reconnect for retryable errors (or normal closures without error)
        return error == null || error.isRetryable
    }

    private inline fun notifyObservers(action: (WebSocketEventObserver) -> Unit) {
        observers.forEach { observer ->
            try { action(observer) } catch (_: Throwable) { /* observer must not break pipeline */ }
        }
    }

    private fun computeDelay(policy: ReconnectPolicy, attempt: Int): Long = when (policy) {
        is ReconnectPolicy.None -> 0L
        is ReconnectPolicy.FixedDelay -> policy.delay.inWholeMilliseconds
        is ReconnectPolicy.ExponentialBackoff -> {
            val calculated = policy.initialDelay.inWholeMilliseconds *
                policy.multiplier.pow(attempt.toDouble())
            minOf(calculated.toLong(), policy.maxDelay.inWholeMilliseconds)
        }
    }
}
