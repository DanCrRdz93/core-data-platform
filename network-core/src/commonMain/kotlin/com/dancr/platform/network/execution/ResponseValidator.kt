package com.dancr.platform.network.execution

import com.dancr.platform.network.client.RawResponse

interface ResponseValidator {

    fun validate(response: RawResponse): ValidationOutcome
}

sealed class ValidationOutcome {

    data object Valid : ValidationOutcome()

    data class Invalid(
        val reason: String,
        val statusCode: Int? = null
    ) : ValidationOutcome()
}
