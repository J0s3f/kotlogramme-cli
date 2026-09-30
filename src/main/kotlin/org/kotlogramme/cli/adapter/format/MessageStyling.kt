package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.OutputFormat
import org.kotlogramme.cli.domain.MessageEntity

/**
 * Renders a message's formatting entities for the terminal.
 *
 * A terminal can express bold, italic, underline, strikethrough and colour, but not a font change,
 * a hidden spoiler or a clickable link, so the mapping is deliberately a small, documented table
 * rather than an attempt to be a rich-text renderer:
 *
 * - `bold`, `italic`, `underline` and `strike` use their SGR attribute.
 * - `code` and `pre` use cyan, because the terminal already renders the text in its one font.
 * - `url` and `textUrl` use a blue underline; `mention`, `mentionName`, `hashtag`, `cashtag`,
 *   `botCommand`, `email`, `phone` and `bankCard` use cyan.
 * - `spoiler` has no terminal equivalent. It is not printed: each character is replaced by a full
 *   block (`█`) of the same length, so the content stays hidden and the column stays aligned.
 *   This applies in the table format whether or not colour is on.
 * - Every other kind — and any later kind this build does not know — is left as plain text.
 *
 * A span that is negative, empty or reaches past the text is ignored rather than allowed to throw.
 * Entities are applied per position, closing the styles that end before opening the ones that
 * start, so overlapping and nested spans compose. When [color] is off the ANSI codes are omitted;
 * spoiler masking is not colour and stays on when the caller asks for it.
 */
class MessageStyler private constructor(
    private val color: Boolean,
    private val maskSpoilers: Boolean,
) {
    /** [text] with the styles of [entities] applied. */
    fun style(text: String, entities: List<MessageEntity>): String {
        if (text.isEmpty() || entities.isEmpty()) return text
        val spans = entities.mapNotNull { it.toSpan(text.length) }
        if (spans.isEmpty()) return text

        val states = Array(text.length) { mutableListOf<Span>() }
        val masked = BooleanArray(text.length)
        for (span in spans) {
            for (index in span.start until span.end) {
                if (span.mask) masked[index] = true else states[index].add(span)
            }
        }

        val rendered = StringBuilder(text.length)
        var previous: List<Span> = emptyList()
        for (index in 0..text.length) {
            val current = if (index < text.length) states[index] else emptyList()
            previous.filter { it !in current }
                .sortedByDescending { it.start }
                .forEach { rendered.append(it.close) }
            current.filter { it !in previous }
                .sortedBy { it.start }
                .forEach { rendered.append(it.open) }
            if (index < text.length) {
                rendered.append(if (masked[index]) SPOILER_MASK else text[index])
            }
            previous = current
        }
        return rendered.toString()
    }

    private fun MessageEntity.toSpan(textLength: Int): Span? {
        if (offset < 0 || length <= 0 || offset + length > textLength) return null
        val end = offset + length
        if (kind == SPOILER) {
            return if (maskSpoilers) Span(offset, end, open = "", close = "", mask = true) else null
        }
        val style = if (color) STYLES[kind] else null
        return style?.let { Span(offset, end, it.open, it.close, mask = false) }
    }

    private data class Span(val start: Int, val end: Int, val open: String, val close: String, val mask: Boolean)

    companion object {
        /** No entity rendering: plain text for the plain and JSON formats, and for tests. */
        val PLAIN = MessageStyler(color = false, maskSpoilers = false)

        /** Entity rendering for the table format; [color] turns the ANSI codes on or off. */
        fun table(color: Boolean): MessageStyler = MessageStyler(color = color, maskSpoilers = true)

        private const val SPOILER = "spoiler"

        private const val SPOILER_MASK = '\u2588'

        private data class AnsiStyle(val open: String, val close: String)

        private fun sgr(vararg codes: Int) = "\u001B[${codes.joinToString(";")}m"

        private val BOLD = AnsiStyle(sgr(1), sgr(22))
        private val ITALIC = AnsiStyle(sgr(3), sgr(23))
        private val UNDERLINE = AnsiStyle(sgr(4), sgr(24))
        private val STRIKE = AnsiStyle(sgr(9), sgr(29))
        private val CYAN = AnsiStyle(sgr(36), sgr(39))
        private val LINK = AnsiStyle(sgr(4, 34), sgr(24, 39))

        private val STYLES = mapOf(
            "bold" to BOLD,
            "italic" to ITALIC,
            "underline" to UNDERLINE,
            "strike" to STRIKE,
            "code" to CYAN,
            "pre" to CYAN,
            "url" to LINK,
            "textUrl" to LINK,
            "mention" to CYAN,
            "mentionName" to CYAN,
            "hashtag" to CYAN,
            "cashtag" to CYAN,
            "botCommand" to CYAN,
            "email" to CYAN,
            "phone" to CYAN,
            "bankCard" to CYAN,
        )
    }
}

/**
 * The visible length of [text], ignoring the ANSI SGR sequences it may carry.
 *
 * The table measures and pads with this so a styled cell and a plain one share a column without the
 * escapes counting towards the width.
 */
internal fun visibleLength(text: String): Int = ANSI_SGR.replace(text, "").length

private val ANSI_SGR = Regex("\u001B\\[[0-9;]*m")

/** Whether ANSI colour may be emitted: the flag, `NO_COLOR` and a terminal decide together. */
internal fun colorEnabled(noColor: Boolean, environment: Map<String, String>, terminal: Boolean): Boolean =
    !noColor && environment["NO_COLOR"].isNullOrEmpty() && terminal

/** The styler a command should render messages with for [format]. */
internal fun messageStylerFor(format: OutputFormat, color: Boolean): MessageStyler = when (format) {
    OutputFormat.TABLE -> MessageStyler.table(color)
    OutputFormat.PLAIN, OutputFormat.JSON -> MessageStyler.PLAIN
}
