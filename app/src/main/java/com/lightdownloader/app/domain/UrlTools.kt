package com.lightdownloader.app.domain

import android.net.Uri

/** URL parsing helpers shared by the view model and the extraction cache. */
object UrlTools {

    /** Adds a scheme when missing and rejects anything that is not http(s). */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val candidate = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (uri.host.isNullOrBlank()) return null
        return candidate
    }

    /**
     * Returns the YouTube video id for the common URL shapes (watch, youtu.be,
     * shorts, embed, live). Returns null for other sites or incomplete links.
     */
    fun videoId(input: String): String? {
        val normalized = normalize(input) ?: return null
        val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null

        val candidate = when {
            host == "youtu.be" || host.endsWith(".youtu.be") ->
                uri.pathSegments.firstOrNull()

            host == "youtube.com" || host.endsWith(".youtube.com") ||
                host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com") -> {
                uri.getQueryParameter("v") ?: when (uri.pathSegments.firstOrNull()) {
                    "shorts", "embed", "live", "v" -> uri.pathSegments.getOrNull(1)
                    else -> null
                }
            }

            else -> null
        }

        return candidate?.takeIf { id ->
            id.length in 5..20 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        }
    }

    /** True when the text already looks like a complete, fetchable video link. */
    fun isCompleteVideoLink(input: String): Boolean = videoId(input) != null
}
