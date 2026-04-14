package com.dancr.platform.network.result

data class Diagnostic(
    val description: String,
    val cause: Throwable? = null,
    val metadata: Map<String, String> = emptyMap()
)
