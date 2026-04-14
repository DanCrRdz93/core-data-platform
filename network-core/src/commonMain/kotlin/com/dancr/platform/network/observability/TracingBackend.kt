package com.dancr.platform.network.observability

// Abstraction for distributed tracing.
// The SDK never decides the tracing backend — the consumer provides it.
// No-op by default so the SDK generates no traces unless explicitly configured.
//
// Consumers wire this to their tracing system (OpenTelemetry, Datadog, Zipkin, etc.):
//     val tracing = object : TracingBackend {
//         override fun generateSpanId() = UUID.randomUUID().toString()
//         override fun generateTraceId() = UUID.randomUUID().toString()
//     }
interface TracingBackend {

    // Generates a new span ID for a request. Called once per request attempt.
    fun generateSpanId(): String

    // Generates a new trace ID. Called when no parentSpanId is present in RequestContext.
    fun generateTraceId(): String

    // Called when a network span starts (request initiated).
    fun onSpanStarted(
        traceId: String,
        spanId: String,
        operationName: String,
        tags: Map<String, String> = emptyMap()
    ) {}

    // Called when a network span finishes (response received or request failed).
    fun onSpanFinished(
        operationName: String,
        durationMs: Long,
        tags: Map<String, String> = emptyMap(),
        error: Throwable? = null
    ) {}

    companion object {
        val NOOP: TracingBackend = object : TracingBackend {
            override fun generateSpanId(): String = ""
            override fun generateTraceId(): String = ""
        }
    }
}
