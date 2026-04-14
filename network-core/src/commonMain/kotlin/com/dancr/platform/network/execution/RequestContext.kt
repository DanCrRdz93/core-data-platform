package com.dancr.platform.network.execution

import com.dancr.platform.network.config.RetryPolicy

data class RequestContext(
    val operationId: String,
    val tags: Map<String, String> = emptyMap(),
    val retryPolicyOverride: RetryPolicy? = null,
    // Set by the consumer to propagate distributed trace context.
    // TracingObserver reads this to correlate spans; a RequestInterceptor can
    // propagate it as a header (e.g. X-Trace-Id) to downstream services.
    val parentSpanId: String? = null,
    // Signals that this request requires authentication.
    // Auth-aware interceptors can use this to skip credential injection for
    // unauthenticated endpoints, or to customize retry behavior on 401.
    val requiresAuth: Boolean = false
)
