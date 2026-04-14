package com.dancr.platform.network.observability

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.execution.RequestContext
import com.dancr.platform.network.result.NetworkError

// Extension point for observability: metrics, tracing, and diagnostics.
// Default methods are no-op so implementors only override what they need.
//
// TODO: Implement MetricsObserver — collect request count, latency histograms, error rates.
// TODO: Implement TracingObserver — create spans per request, propagate trace context via headers.
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
