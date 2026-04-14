package com.dancr.platform.network.observability

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.execution.RequestContext
import com.dancr.platform.network.result.NetworkError

// Extension point for observability: metrics, tracing, and diagnostics.
// Default methods are no-op so implementors only override what they need.
//
// Built-in implementations:
//   LoggingObserver  — logs lifecycle events via injectable NetworkLogger.
//   MetricsObserver  — records latency/error/retry metrics via injectable MetricsCollector.
//   TracingObserver  — generates span/trace IDs via injectable TracingBackend.
interface NetworkEventObserver {

    fun onRequestStarted(request: HttpRequest, context: RequestContext?) {}

    fun onResponseReceived(
        request: HttpRequest,
        response: RawResponse,
        durationMs: Long,
        context: RequestContext?
    ) {}

    fun onRetryScheduled(
        request: HttpRequest,
        attempt: Int,
        maxAttempts: Int,
        error: NetworkError,
        delayMs: Long
    ) {}

    fun onRequestFailed(
        request: HttpRequest,
        error: NetworkError,
        durationMs: Long,
        context: RequestContext?
    ) {}

    companion object {
        val NOOP: NetworkEventObserver = object : NetworkEventObserver {}
    }
}
