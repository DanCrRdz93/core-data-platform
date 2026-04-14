package com.dancr.platform.network.observability

// Abstraction for collecting network metrics.
// The SDK never decides the metrics backend — the consumer provides it.
// No-op by default so the SDK collects nothing unless explicitly configured.
//
// Consumers wire this to their metrics system (Micrometer, Datadog, custom, etc.):
//     val collector = MetricsCollector { name, value, tags ->
//         datadogClient.gauge(name, value, tags)
//     }
interface MetricsCollector {

    // Records a timing measurement (e.g. request duration in milliseconds).
    fun recordTiming(name: String, durationMs: Long, tags: Map<String, String> = emptyMap())

    // Increments a counter (e.g. request count, error count).
    fun increment(name: String, tags: Map<String, String> = emptyMap())

    companion object {
        val NOOP: MetricsCollector = object : MetricsCollector {
            override fun recordTiming(name: String, durationMs: Long, tags: Map<String, String>) {}
            override fun increment(name: String, tags: Map<String, String>) {}
        }
    }
}
