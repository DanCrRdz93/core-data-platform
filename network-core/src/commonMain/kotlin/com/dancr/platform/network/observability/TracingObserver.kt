package com.dancr.platform.network.observability

import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.execution.RequestContext
import com.dancr.platform.network.result.NetworkError

// Observes network lifecycle events and delegates trace context to a TracingBackend.
// Generates span/trace IDs and records them alongside request metadata.
// No-op by default — the consumer controls the tracing backend.
//
// The observer generates IDs and reports them to the backend. It does NOT mutate
// the request (header propagation is the responsibility of a RequestInterceptor
// that reads parentSpanId from RequestContext).
//
// Typical setup:
//     val tracingObserver = TracingObserver(backend = myOpenTelemetryBackend)
//     // Pair with a RequestInterceptor that propagates trace headers:
//     val tracingInterceptor = RequestInterceptor { request, context ->
//         val spanId = context?.parentSpanId ?: ""
//         request.copy(headers = request.headers + ("X-Trace-Id" to spanId))
//     }
class TracingObserver(
    private val backend: TracingBackend = TracingBackend.NOOP
) : NetworkEventObserver {

    override fun onRequestStarted(request: HttpRequest, context: RequestContext?) {
        val traceId = context?.parentSpanId ?: backend.generateTraceId()
        val spanId = backend.generateSpanId()
        if (traceId.isNotEmpty() || spanId.isNotEmpty()) {
            // Store IDs are available for the consumer's tracing backend
            // to correlate with their system. The backend decides what to do.
            backend.onSpanStarted(
                traceId = traceId,
                spanId = spanId,
                operationName = "${request.method.name} ${sanitizePath(request.path)}",
                tags = buildMap {
                    put("http.method", request.method.name)
                    put("http.path", sanitizePath(request.path))
                    context?.operationId?.let { put("operation.id", it) }
                }
            )
        }
    }

    override fun onResponseReceived(
        request: HttpRequest,
        response: RawResponse,
        durationMs: Long,
        context: RequestContext?
    ) {
        backend.onSpanFinished(
            operationName = "${request.method.name} ${sanitizePath(request.path)}",
            durationMs = durationMs,
            tags = mapOf(
                "http.method" to request.method.name,
                "http.path" to sanitizePath(request.path),
                "http.status_code" to response.statusCode.toString(),
                "http.success" to response.isSuccessful.toString()
            ),
            error = null
        )
    }

    override fun onRequestFailed(
        request: HttpRequest,
        error: NetworkError,
        durationMs: Long,
        context: RequestContext?
    ) {
        backend.onSpanFinished(
            operationName = "${request.method.name} ${sanitizePath(request.path)}",
            durationMs = durationMs,
            tags = mapOf(
                "http.method" to request.method.name,
                "http.path" to sanitizePath(request.path),
                "error.type" to (error::class.simpleName ?: "Unknown"),
                "error.message" to error.message
            ),
            error = error.diagnostic?.cause
        )
    }

    // OWASP MASVS-PRIVACY: strip query parameters from paths to prevent
    // accidental token/secret leakage through tracing backends.
    private fun sanitizePath(path: String): String = path.substringBefore("?")
}
