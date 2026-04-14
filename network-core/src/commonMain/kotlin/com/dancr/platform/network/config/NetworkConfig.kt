package com.dancr.platform.network.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class NetworkConfig(
    val baseUrl: String,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val connectTimeout: Duration = 30.seconds,
    val readTimeout: Duration = 30.seconds,
    val writeTimeout: Duration = 30.seconds,
    val retryPolicy: RetryPolicy = RetryPolicy.None
) {
    init {
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
    }
}
