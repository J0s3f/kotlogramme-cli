package org.kotlogramme.cli.adapter.cli

/**
 * Decodes the emoji argument of `react`.
 *
 * Windows hands a JVM its command line in the ANSI code page, so a literal emoji typed at a
 * non-UTF-8 console arrives as `?` and Telegram rejects it. Callers can pass the code point
 * instead — `U+1F44D`, `u+1f44d` or the Java form `\uD83D\uDC4D` — and the client turns it into the
 * emoji before the argument crosses the port. A literal emoji is returned unchanged.
 */
internal fun decodeEmojiArgument(raw: String): String {
    val text = raw.trim()
    CODE_POINT.matchEntire(text)?.let { match ->
        val value = match.groupValues[1].toInt(16)
        require(Character.isValidCodePoint(value)) { "Not a Unicode code point: $raw" }
        return String(Character.toChars(value))
    }
    if (text.startsWith("\\u")) {
        val match = JAVA_ESCAPE.matchEntire(text)
            ?: throw IllegalArgumentException("Not a Unicode escape: $raw")
        return match.groupValues.drop(1)
            .filter(String::isNotEmpty)
            .joinToString("") { escape -> escape.toInt(16).toChar().toString() }
    }
    return text
}

private val CODE_POINT = Regex("^[Uu]\\+([0-9a-fA-F]{1,6})$")

private val JAVA_ESCAPE = Regex("^\\\\u([0-9a-fA-F]{4})(?:\\\\u([0-9a-fA-F]{4}))*$")
