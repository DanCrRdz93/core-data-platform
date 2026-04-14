package com.dancr.platform.security.trust

sealed interface TrustEvaluation {

    data object Trusted : TrustEvaluation

    data class Denied(val reason: String) : TrustEvaluation
}
