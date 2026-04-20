package com.dancr.platform.network.client

/**
 * Standard HTTP methods supported by [HttpRequest].
 *
 * **Example:**
 * ```kotlin
 * val request = HttpRequest(
 *     path = "/users",
 *     method = HttpMethod.GET
 * )
 * ```
 */
enum class HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS
}
