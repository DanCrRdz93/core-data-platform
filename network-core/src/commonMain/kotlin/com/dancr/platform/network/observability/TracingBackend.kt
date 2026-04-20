package com.dancr.platform.network.observability

/**
 * Abstraction for distributed tracing.
 *
 * The SDK never decides the tracing backend — the consumer provides it.
 * No-op by default so the SDK generates no traces unless explicitly configured.
 *
 * **Example — OpenTelemetry implementation:**
 * ```kotlin
 * val tracing = object : TracingBackend {
 *     override fun generateSpanId() = UUID.randomUUID().toString()
 *     override fun generateTraceId() = UUID.randomUUID().toString()
 *
 *     override fun onSpanStarted(traceId: String, spanId: String, operationName: String, tags: Map<String, String>) {
 *         otelTracer.spanBuilder(operationName)
 *             .setSpanKind(SpanKind.CLIENT)
 *             .startSpan()
 *     }
 *
 *     override fun onSpanFinished(operationName: String, durationMs: Long, tags: Map<String, String>, error: Throwable?) {
 *         currentSpan.end()
 *     }
 * }
 * ```
 *
 * @see TracingObserver for the built-in observer that delegates to this backend.
 */
interface TracingBackend {

    /** Generates a new span ID for a request. Called once per request attempt. */
    fun generateSpanId(): String

    /** Generates a new trace ID. Called when no `parentSpanId` is present in [RequestContext][com.dancr.platform.network.execution.RequestContext]. */
    fun generateTraceId(): String

    /**
     * Called when a network span starts (request initiated).
     *
     * @param traceId       The trace ID (from parent context or newly generated).
     * @param spanId        The span ID for this request attempt.
     * @param operationName Human-readable operation name (e.g. `"GET /users"`).
     * @param tags          Arbitrary key-value tags for the span.
     */
    fun onSpanStarted(
        traceId: String,
        spanId: String,
        operationName: String,
        tags: Map<String, String> = emptyMap()
    ) {}

    /**
     * Called when a network span finishes (response received or request failed).
     *
     * @param operationName Human-readable operation name.
     * @param durationMs    Span duration in milliseconds.
     * @param tags          Arbitrary key-value tags for the span.
     * @param error         Optional [Throwable] if the span ended with an error.
     */
    fun onSpanFinished(
        operationName: String,
        durationMs: Long,
        tags: Map<String, String> = emptyMap(),
        error: Throwable? = null
    ) {}

    companion object {
        /** No-op backend that generates empty IDs and ignores all span events. */
        val NOOP: TracingBackend = object : TracingBackend {
            override fun generateSpanId(): String = ""
            override fun generateTraceId(): String = ""
        }
    }
}
