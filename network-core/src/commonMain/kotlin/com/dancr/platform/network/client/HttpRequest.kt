package com.dancr.platform.network.client

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
}
