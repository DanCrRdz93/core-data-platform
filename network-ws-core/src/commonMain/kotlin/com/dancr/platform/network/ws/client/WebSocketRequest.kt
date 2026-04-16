package com.dancr.platform.network.ws.client

data class WebSocketRequest(
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val protocols: List<String> = emptyList()
) {
    // OWASP MASVS-STORAGE-2: Never log header values (may contain auth tokens).
    override fun toString(): String =
        "WebSocketRequest(path=$path, headers=[${headers.keys.joinToString()}], protocols=$protocols)"
}
