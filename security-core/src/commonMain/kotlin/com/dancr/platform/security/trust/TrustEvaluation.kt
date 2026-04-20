package com.dancr.platform.security.trust

/**
 * Result of [TrustPolicy.evaluateHost] — indicates whether a host is trusted.
 *
 * **Example:**
 * ```kotlin
 * when (val result = trustPolicy.evaluateHost("api.example.com")) {
 *     is TrustEvaluation.Trusted -> proceedWithConnection()
 *     is TrustEvaluation.Denied  -> abortConnection(result.reason)
 * }
 * ```
 *
 * @see TrustPolicy
 */
sealed interface TrustEvaluation {

    /** The host passed trust evaluation. */
    data object Trusted : TrustEvaluation

    /**
     * The host was denied.
     *
     * @property reason Human-readable explanation of why the host was not trusted.
     */
    data class Denied(val reason: String) : TrustEvaluation
}
