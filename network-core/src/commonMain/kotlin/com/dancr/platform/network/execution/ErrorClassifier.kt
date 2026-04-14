package com.dancr.platform.network.execution

import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.result.NetworkError

// Extension point for transport-aware error classification.
// Implement per transport module (e.g. KtorErrorClassifier) for type-safe exception matching.
// TODO: Add classifyForRetry(error, attempt) when circuit-breaker logic is needed.
interface ErrorClassifier {

    fun classify(response: RawResponse?, cause: Throwable?): NetworkError
}
