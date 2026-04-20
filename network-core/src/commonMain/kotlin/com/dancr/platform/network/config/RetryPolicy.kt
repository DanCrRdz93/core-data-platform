package com.dancr.platform.network.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Retry strategy applied to failed network requests.
 *
 * Set as the default in [NetworkConfig.retryPolicy] or override per-request
 * via [RequestContext.retryPolicyOverride][com.dancr.platform.network.execution.RequestContext.retryPolicyOverride].
 * Only errors with [NetworkError.isRetryable][com.dancr.platform.network.result.NetworkError.isRetryable] = `true`
 * are retried.
 *
 * **Example:**
 * ```kotlin
 * // No retries (default)
 * val none = RetryPolicy.None
 *
 * // Fixed 1-second delay, up to 3 retries
 * val fixed = RetryPolicy.FixedDelay(maxRetries = 3, delay = 1.seconds)
 *
 * // Exponential backoff: 1s, 2s, 4s, … capped at 30s
 * val expo = RetryPolicy.ExponentialBackoff(
 *     maxRetries = 5,
 *     initialDelay = 1.seconds,
 *     maxDelay = 30.seconds,
 *     multiplier = 2.0
 * )
 * ```
 *
 * @see NetworkConfig.retryPolicy
 */
sealed class RetryPolicy {

    /** No automatic retries. The request is attempted exactly once. */
    data object None : RetryPolicy()

    /**
     * Retries with a constant [delay] between each attempt.
     *
     * @property maxRetries Maximum number of retry attempts (must be > 0).
     * @property delay      Wait duration between retries.
     */
    data class FixedDelay(
        val maxRetries: Int,
        val delay: Duration = 1.seconds
    ) : RetryPolicy() {
        init {
            require(maxRetries > 0) { "maxRetries must be greater than 0" }
        }
    }

    /**
     * Retries with exponentially increasing delays.
     *
     * Delay doubles (by [multiplier]) after each attempt, capped at [maxDelay].
     *
     * @property maxRetries   Maximum number of retry attempts (must be > 0).
     * @property initialDelay Delay before the first retry.
     * @property maxDelay     Upper bound for the computed delay.
     * @property multiplier   Factor applied to the delay after each attempt (must be > 1.0).
     */
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
