package com.dancr.platform.security.sanitizer

interface LogSanitizer {

    fun sanitize(key: String, value: String): String
}

fun LogSanitizer.sanitizeHeaders(headers: Map<String, String>): Map<String, String> =
    headers.mapValues { (key, value) -> sanitize(key, value) }

fun LogSanitizer.sanitizeMultiValueHeaders(headers: Map<String, List<String>>): Map<String, List<String>> =
    headers.mapValues { (key, values) -> values.map { sanitize(key, it) } }
