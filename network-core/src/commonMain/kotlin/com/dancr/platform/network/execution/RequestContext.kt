package com.dancr.platform.network.execution

import com.dancr.platform.network.config.RetryPolicy

data class RequestContext(
    val operationId: String,
    val tags: Map<String, String> = emptyMap(),
    val retryPolicyOverride: RetryPolicy? = null,
    // TODO: Populate from TracingObserver to enable distributed trace propagation.
    val parentSpanId: String? = null,
    // TODO: Use to override which errors are retryable for specific operations.
    val requiresAuth: Boolean = false
)
