package com.dancr.platform.network.ws.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Reconnection strategy after an unexpected disconnection.
// Analogous to RetryPolicy for HTTP, but for persistent connections.
sealed class ReconnectPolicy {

    // No automatic reconnection. The connection Flow completes on disconnect.
    data object None : ReconnectPolicy()

    // Reconnect after a fixed delay between each attempt.
    data class FixedDelay(
        val maxAttempts: Int = 5,
        val delay: Duration = 3.seconds
    ) : ReconnectPolicy() {
        init {
            require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
        }
    }

    // Reconnect with exponential backoff. Delay doubles (up to maxDelay) after each attempt.
    data class ExponentialBackoff(
        val maxAttempts: Int = 10,
        val initialDelay: Duration = 1.seconds,
        val maxDelay: Duration = 30.seconds,
        val multiplier: Double = 2.0
    ) : ReconnectPolicy() {
        init {
            require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
            require(multiplier >= 1.0) { "multiplier must be >= 1.0, was $multiplier" }
        }
    }
}
