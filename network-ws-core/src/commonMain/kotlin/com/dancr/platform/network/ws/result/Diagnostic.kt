package com.dancr.platform.network.ws.result

data class Diagnostic(
    val description: String,
    val cause: Throwable? = null,
    val metadata: Map<String, String> = emptyMap()
)
