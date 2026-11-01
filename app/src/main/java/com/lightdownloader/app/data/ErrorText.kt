package com.lightdownloader.app.data

import java.util.Locale

private val ANSI_ESCAPE = Regex("\u001B\\[[0-9;]*[A-Za-z]")

internal fun Throwable.userMessage(): String {
    val raw = message?.takeIf { it.isNotBlank() } ?: return "Something went wrong."
    val clean = raw.replace(ANSI_ESCAPE, "").trim()
    val errorLine = clean.lineSequence()
        .map { it.trim() }
        .lastOrNull { it.startsWith("ERROR:", ignoreCase = true) }
        ?: clean.lineSequence().lastOrNull { it.isNotBlank() }
        ?: clean
    val message = errorLine.removePrefix("ERROR:").trim().take(300)

    return when {
        message.contains("Sign in to confirm", ignoreCase = true) ||
            message.contains("not a bot", ignoreCase = true) ->
            "The source requested verification. Try again shortly, or update the download engine."

        message.contains("Unsupported URL", ignoreCase = true) ->
            "That link isn't supported by the download engine."

        message.contains("Video unavailable", ignoreCase = true) ->
            "This video is unavailable."

        message.contains("Private video", ignoreCase = true) ->
            "This video is private."

        message.contains("HTTP Error 403", ignoreCase = true) ->
            "The source refused the request (403). Try updating the download engine."

        message.contains("timed out", ignoreCase = true) ||
            message.contains("Unable to download", ignoreCase = true) ||
            message.contains("network", ignoreCase = true) ->
            "Network error. Check your connection and try again."

        message.isBlank() -> "Something went wrong."
        else -> message
    }
}

internal fun String.cleanProgressLine(): String =
    replace(ANSI_ESCAPE, "")
        .replace('\r', ' ')
        .trim()
        .take(160)

internal fun formatEta(seconds: Long): String {
    if (seconds <= 0L) return ""
    val minutes = seconds / 60
    val remainder = seconds % 60
    return if (minutes > 0) {
        String.format(Locale.US, "%dm %02ds left", minutes, remainder)
    } else {
        String.format(Locale.US, "%ds left", remainder)
    }
}
