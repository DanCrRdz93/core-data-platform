package com.dancr.platform.network.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class RetryPolicy {

    data object None : RetryPolicy()

    data class FixedDelay(
        val maxRetries: Int,
        val delay: Duration = 1.seconds
    ) : RetryPolicy() {
        init {
            require(maxRetries > 0) { "maxRetries must be greater than 0" }
        }
    }

    data class ExponentialBackoff(
        val maxRetries: Int,
        val initialDelay: Duration = 1.seconds,
        val maxDelay: Duration = 30.seconds,
        val multiplier: Double = 2.0
    ) : RetryPolicy() {
        init {
            require(maxRetries > 0) { "maxRetries must be greater than 0" }
            require(multiplier > 1.0) { "multiplier must be greater than 1.0" }
        }
    }
}
