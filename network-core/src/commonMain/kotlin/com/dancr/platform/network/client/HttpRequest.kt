package com.dancr.platform.network.client

/**
 * Immutable HTTP request descriptor.
 *
 * The [path] is relative to the base URL configured in
 * [NetworkConfig][com.dancr.platform.network.config.NetworkConfig]; the executor
 * merges them before sending. The [toString] override redacts header values
 * (OWASP MASVS-STORAGE-2).
 *
 * **Example — creating a GET request:**
 * ```kotlin
 * val request = HttpRequest(
 *     path = "/users/42",
 *     method = HttpMethod.GET,
 *     headers = mapOf("Accept" to "application/json")
 * )
 * ```
 *
 * **Example — creating a POST request with body:**
 * ```kotlin
 * val request = HttpRequest(
 *     path = "/users",
 *     method = HttpMethod.POST,
 *     headers = mapOf("Content-Type" to "application/json"),
 *     body = """{ "name": "Alice" }""".encodeToByteArray()
 * )
 * ```
 *
 * @property path        Relative path appended to the base URL.
 * @property method      HTTP method (GET, POST, PUT, etc.).
 * @property headers     Request headers (merged with default headers from config).
 * @property queryParams Query parameters appended to the URL.
 * @property body        Optional request body as raw bytes.
 */
data class HttpRequest(
    val path: String,
    val method: HttpMethod,
    val headers: Map<String, String> = emptyMap(),
    val queryParams: Map<String, String> = emptyMap(),
    val body: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as HttpRequest
        return path == other.path &&
            method == other.method &&
            headers == other.headers &&
            queryParams == other.queryParams &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + queryParams.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "HttpRequest(method=${method.name}, path=$path, headers=[${headers.keys.joinToString()}], " +
            "queryParams=[${queryParams.keys.joinToString()}], body=${body?.size ?: 0} bytes)"

    companion object {
        /** `Content-Type: application/json; charset=utf-8`. */
        const val CONTENT_TYPE_JSON: String = "application/json"

        /** Standard JSON `Accept` + `Content-Type` headers, ready to spread or merge. */
        val JSON_HEADERS: Map<String, String> = mapOf(
            "Accept" to CONTENT_TYPE_JSON,
            "Content-Type" to CONTENT_TYPE_JSON,
        )

        /**
         * Builds an [HttpRequest] with `Content-Type: application/json` headers pre-set
         * and an optional [bodyJson] string body.
         *
         * Reduces duplication for typical JSON API calls. Additional [headers] are merged
         * on top of the JSON defaults (consumer wins on conflict).
         *
         * **Example:**
         * ```kotlin
         * val req = HttpRequest.json(
         *     path = "/users",
         *     method = HttpMethod.POST,
         *     bodyJson = json.encodeToString(CreateUserDto(name = "Alice")),
         * )
         * ```
         *
         * @param path        Path relative to the base URL.
         * @param method      HTTP method (typically POST/PUT/PATCH for write operations).
         * @param bodyJson    Optional JSON-encoded body.
         * @param headers     Extra headers to merge on top of the JSON defaults.
         * @param queryParams Optional query parameters.
         */
        fun json(
            path: String,
            method: HttpMethod,
            bodyJson: String? = null,
            headers: Map<String, String> = emptyMap(),
            queryParams: Map<String, String> = emptyMap(),
        ): HttpRequest = HttpRequest(
            path = path,
            method = method,
            headers = JSON_HEADERS + headers,
            queryParams = queryParams,
            body = bodyJson?.encodeToByteArray(),
        )
    }
}
