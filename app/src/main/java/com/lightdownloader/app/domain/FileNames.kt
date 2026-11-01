package com.lightdownloader.app.domain

object FileNames {
    private val invalidChars = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
    private val mediaExtensions = setOf(
        "mp4", "m4v", "mkv", "webm", "mov",
        "mp3", "m4a", "opus", "ogg", "aac", "flac", "wav"
    )

    fun sanitize(input: String, fallback: String): String {
        val trimmed = input.trim().ifBlank { fallback.trim() }
        val looksLikeMediaName = trimmed.contains('.') &&
            trimmed.substringAfterLast('.').lowercase() in mediaExtensions
        val withoutExtension = if (looksLikeMediaName) {
            trimmed.substringBeforeLast('.')
        } else {
            trimmed
        }
        val cleaned = withoutExtension
            .replace(invalidChars, "_")
            .replace('%', '_')
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', ' ')
            .take(120)
        return cleaned.ifBlank { "download" }
    }

    fun extensionFor(category: MediaCategory, audioFormat: AudioFormat): String =
        if (category == MediaCategory.VIDEO) "mp4" else audioFormat.extension
}
