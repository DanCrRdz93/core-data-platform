package com.dancr.platform.network.execution

import com.dancr.platform.network.client.RawResponse

class DefaultResponseValidator : ResponseValidator {

    override fun validate(response: RawResponse): ValidationOutcome {
        return if (response.isSuccessful) {
            ValidationOutcome.Valid
        } else {
            ValidationOutcome.Invalid(
                reason = "HTTP ${response.statusCode}",
                statusCode = response.statusCode
            )
        }
    }
}
