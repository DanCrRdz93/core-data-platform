package com.dancr.platform.network.ws.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Reconnection strategy after an unexpected WebSocket disconnection.
 *
 * Analogous to [RetryPolicy][com.dancr.platform.network.config.RetryPolicy] for HTTP,
 * but designed for persistent connections.
 *
 * **Example:**
 * ```kotlin
 * // No reconnection
 * val none = ReconnectPolicy.None
 *
 * // Fixed 3-second delay, up to 5 attempts
 * val fixed = ReconnectPolicy.FixedDelay(maxAttempts = 5, delay = 3.seconds)
 *
 * // Exponential backoff: 1s, 2s, 4s, … capped at 30s
 * val expo = ReconnectPolicy.ExponentialBackoff(
 *     maxAttempts = 10,
 *     initialDelay = 1.seconds,
 *     maxDelay = 30.seconds
 * )
 * ```
 *
 * @see WebSocketConfig.reconnectPolicy
 */
sealed class ReconnectPolicy {

    /** No automatic reconnection. The connection Flow completes on disconnect. */
    data object None : ReconnectPolicy()

    /**
     * Reconnect after a fixed [delay] between each attempt.
     *
     * @property maxAttempts Maximum number of reconnection attempts (must be ≥ 1).
     * @property delay       Wait duration between reconnection attempts.
     */
    data class FixedDelay(
        val maxAttempts: Int = 5,
        val delay: Duration = 3.seconds
    ) : ReconnectPolicy() {
        init {
            require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }
        }
    }

    /**
     * Reconnect with exponential backoff. Delay is multiplied by [multiplier]
     * after each attempt, capped at [maxDelay].
     *
     * @property maxAttempts   Maximum number of reconnection attempts (must be ≥ 1).
     * @property initialDelay  Delay before the first reconnection attempt.
     * @property maxDelay      Upper bound for the computed delay.
     * @property multiplier    Factor applied to the delay after each attempt (must be ≥ 1.0).
     */
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
