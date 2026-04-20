package com.dancr.platform.network.observability

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.execution.RequestContext
import com.dancr.platform.network.result.NetworkError

/**
 * Extension point for observing network lifecycle events.
 *
 * All methods default to no-op so implementors only override what they need.
 *
 * Built-in implementations:
 * - [LoggingObserver]  — logs lifecycle events via injectable [NetworkLogger].
 * - [MetricsObserver]  — records latency / error / retry metrics via injectable [MetricsCollector].
 * - [TracingObserver]  — generates span / trace IDs via injectable [TracingBackend].
 *
 * **Example — custom observer:**
 * ```kotlin
 * val analyticsObserver = object : NetworkEventObserver {
 *     override fun onRequestFailed(request: HttpRequest, error: NetworkError, durationMs: Long, context: RequestContext?) {
 *         analytics.track("network_error", mapOf("path" to request.path, "error" to error.message))
 *     }
 * }
 *
 * val executor = DefaultSafeRequestExecutor(
 *     engine = engine,
 *     config = config,
 *     observers = listOf(LoggingObserver(logger), analyticsObserver)
 * )
 * ```
 *
 * @see DefaultSafeRequestExecutor for where observers are notified.
 */
interface NetworkEventObserver {

    /** Called when a request is about to be sent (after interceptors). */
    fun onRequestStarted(request: HttpRequest, context: RequestContext?) {}

    /** Called when a response is received from the engine (before validation). */
    fun onResponseReceived(
        request: HttpRequest,
        response: RawResponse,
        durationMs: Long,
        context: RequestContext?
    ) {}

    /** Called when a retry is scheduled after a retryable failure. */
    fun onRetryScheduled(
        request: HttpRequest,
        attempt: Int,
        maxAttempts: Int,
        error: NetworkError,
        delayMs: Long
    ) {}

    /** Called when a request fails (transport error, validation failure, or deserialization error). */
    fun onRequestFailed(
        request: HttpRequest,
        error: NetworkError,
        durationMs: Long,
        context: RequestContext?
    ) {}

    companion object {
        /** No-op observer that ignores all events. */
        val NOOP: NetworkEventObserver = object : NetworkEventObserver {}
    }
}
