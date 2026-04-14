package com.dancr.platform.network.execution

import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.result.NetworkError

// Extension point for transport-aware error classification.
// Implement per transport module (e.g. KtorErrorClassifier) for type-safe exception matching.
//
// Future: classifyForRetry(error, attempt) for circuit-breaker patterns where
// classification needs to consider attempt count (e.g. open circuit after N failures).
interface ErrorClassifier {

    fun classify(response: RawResponse?, cause: Throwable?): NetworkError
}
