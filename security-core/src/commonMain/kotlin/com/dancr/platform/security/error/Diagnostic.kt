package com.dancr.platform.security.error

data class Diagnostic(
    val description: String,
    val cause: Throwable? = null,
    val metadata: Map<String, String> = emptyMap()
)
