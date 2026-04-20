package com.dancr.platform.network.observability

/**
 * Abstraction for collecting network metrics.
 *
 * The SDK never decides the metrics backend — the consumer provides it.
 * No-op by default so the SDK collects nothing unless explicitly configured.
 *
 * **Example — Datadog implementation:**
 * ```kotlin
 * val collector = object : MetricsCollector {
 *     override fun recordTiming(name: String, durationMs: Long, tags: Map<String, String>) {
 *         datadogClient.gauge(name, durationMs.toDouble(), tags)
 *     }
 *     override fun increment(name: String, tags: Map<String, String>) {
 *         datadogClient.count(name, 1, tags)
 *     }
 * }
 * ```
 *
 * @see MetricsObserver for the built-in observer that delegates to this collector.
 */
interface MetricsCollector {

    /**
     * Records a timing measurement (e.g. request duration in milliseconds).
     *
     * @param name       Metric name (e.g. `"http.client.request.duration"`).
     * @param durationMs Duration in milliseconds.
     * @param tags       Arbitrary key-value tags for grouping/filtering.
     */
    fun recordTiming(name: String, durationMs: Long, tags: Map<String, String> = emptyMap())

    /**
     * Increments a counter (e.g. request count, error count).
     *
     * @param name Metric name (e.g. `"http.client.errors"`).
     * @param tags Arbitrary key-value tags for grouping/filtering.
     */
    fun increment(name: String, tags: Map<String, String> = emptyMap())

    companion object {
        /** No-op collector that silently discards all metrics. */
        val NOOP: MetricsCollector = object : MetricsCollector {
            override fun recordTiming(name: String, durationMs: Long, tags: Map<String, String>) {}
            override fun increment(name: String, tags: Map<String, String>) {}
        }
    }
}
