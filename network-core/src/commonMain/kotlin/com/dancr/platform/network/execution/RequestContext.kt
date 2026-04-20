package com.dancr.platform.network.execution

import com.dancr.platform.network.config.RetryPolicy

/**
 * Per-request metadata that flows through the execution pipeline.
 *
 * Attach a [RequestContext] to any request to propagate tracing, override retry
 * behaviour, or signal authentication requirements.
 *
 * **Example — traced request with custom retry:**
 * ```kotlin
 * val context = RequestContext(
 *     operationId = "getUser-42",
 *     tags = mapOf("feature" to "profile"),
 *     retryPolicyOverride = RetryPolicy.FixedDelay(maxRetries = 2),
 *     parentSpanId = "abc-123-span",
 *     requiresAuth = true
 * )
 *
 * val result = executor.execute(request, context) { response ->
 *     deserialize(response)
 * }
 * ```
 *
 * @property operationId        Unique identifier for this operation (used in logs, metrics, tracing).
 * @property tags               Arbitrary key-value pairs attached to metrics and traces.
 * @property retryPolicyOverride Overrides the default [RetryPolicy] from [NetworkConfig][com.dancr.platform.network.config.NetworkConfig] for this request.
 * @property parentSpanId       Distributed trace context. [TracingObserver][com.dancr.platform.network.observability.TracingObserver]
 *                              reads this to correlate spans; a [RequestInterceptor] can propagate it
 *                              as a header (e.g. `X-Trace-Id`) to downstream services.
 * @property requiresAuth       Signals that this request requires authentication. Auth-aware
 *                              interceptors can skip credential injection for unauthenticated
 *                              endpoints, or customize retry behaviour on 401.
 */
data class RequestContext(
    val operationId: String,
    val tags: Map<String, String> = emptyMap(),
    val retryPolicyOverride: RetryPolicy? = null,
    val parentSpanId: String? = null,
    val requiresAuth: Boolean = false
)
