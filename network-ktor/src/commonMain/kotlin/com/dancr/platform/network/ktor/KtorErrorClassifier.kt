package com.dancr.platform.network.ktor

import com.dancr.platform.network.execution.DefaultErrorClassifier
import com.dancr.platform.network.result.Diagnostic
import com.dancr.platform.network.result.NetworkError
import io.ktor.client.plugins.HttpRequestTimeoutException

class KtorErrorClassifier : DefaultErrorClassifier() {

    override fun classifyThrowable(cause: Throwable): NetworkError {
        if (cause is HttpRequestTimeoutException) {
            return NetworkError.Timeout(
                diagnostic = Diagnostic(
                    description = cause.message ?: "Request timed out",
                    cause = cause
                )
            )
        }

        return super.classifyThrowable(cause)
    }
}
