package com.dancr.platform.network.ws.client

/**
 * Immutable WebSocket connection request descriptor.
 *
 * The [path] is relative to the base URL configured in
 * [WebSocketConfig][com.dancr.platform.network.ws.config.WebSocketConfig].
 * The [toString] override redacts header values (OWASP MASVS-STORAGE-2).
 *
 * **Example:**
 * ```kotlin
 * val request = WebSocketRequest(
 *     path = "/chat/room/42",
 *     headers = mapOf("Authorization" to "Bearer eyJ..."),
 *     protocols = listOf("graphql-ws")
 * )
 * ```
 *
 * @property path      Relative path appended to the base URL.
 * @property headers   Connection headers (merged with default headers from config).
 * @property protocols WebSocket sub-protocols to negotiate.
 */
data class WebSocketRequest(
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val protocols: List<String> = emptyList()
) {
    // OWASP MASVS-STORAGE-2: Never log header values (may contain auth tokens).
    override fun toString(): String =
        "WebSocketRequest(path=$path, headers=[${headers.keys.joinToString()}], protocols=$protocols)"
}
