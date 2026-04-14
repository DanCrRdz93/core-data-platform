package com.dancr.platform.network.observability

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.execution.RequestContext
import com.dancr.platform.network.result.NetworkError

// Observes network lifecycle events and delegates metrics to a MetricsCollector.
// Records request latency, error counts, and retry counts.
// No-op by default — the consumer controls the metrics backend.
class MetricsObserver(
    private val collector: MetricsCollector = MetricsCollector.NOOP,
    private val prefix: String = "http.client"
) : NetworkEventObserver {

    override fun onRequestStarted(request: HttpRequest, context: RequestContext?) {
        collector.increment(
            "$prefix.requests.started",
            baseTags(request, context)
        )
    }

    override fun onResponseReceived(
        request: HttpRequest,
        response: RawResponse,
        durationMs: Long,
        context: RequestContext?
    ) {
        val tags = baseTags(request, context) + mapOf(
            "status" to response.statusCode.toString(),
            "success" to response.isSuccessful.toString()
        )
        collector.recordTiming("$prefix.request.duration", durationMs, tags)
        collector.increment("$prefix.responses", tags)
    }

    override fun onRetryScheduled(
        request: HttpRequest,
        attempt: Int,
        maxAttempts: Int,
        error: NetworkError,
        delayMs: Long
    ) {
        collector.increment(
            "$prefix.retries",
            mapOf(
                "method" to request.method.name,
                "path" to request.path,
                "attempt" to attempt.toString(),
                "error_type" to errorType(error)
            )
        )
    }

    override fun onRequestFailed(
        request: HttpRequest,
        error: NetworkError,
        durationMs: Long,
        context: RequestContext?
    ) {
        val tags = baseTags(request, context) + mapOf(
            "error_type" to errorType(error)
        )
        collector.recordTiming("$prefix.request.duration", durationMs, tags + ("success" to "false"))
        collector.increment("$prefix.errors", tags)
    }

    // -- Internal --

    private fun baseTags(request: HttpRequest, context: RequestContext?): Map<String, String> =
        buildMap {
            put("method", request.method.name)
            put("path", sanitizePath(request.path))
            context?.operationId?.let { put("operation", it) }
        }

    // OWASP MASVS-PRIVACY: strip query parameters from paths to prevent
    // accidental token/secret leakage through metrics backends.
    private fun sanitizePath(path: String): String = path.substringBefore("?")

    private fun errorType(error: NetworkError): String =
        error::class.simpleName ?: "Unknown"
}
