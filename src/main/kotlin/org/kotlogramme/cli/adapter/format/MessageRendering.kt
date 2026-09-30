package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.MediaInfo
import org.kotlogramme.cli.domain.Message
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Renders a page of messages.
 *
 * The row is plain data so every [Output] format carries the same fields: the inline bot, the
 * reply marker, the media label and the service action are their own columns, not concatenated
 * into the text.
 */
internal val MESSAGE_HEADERS = listOf("id", "time", "from", "via", "reply", "media", "action", "text")

/** Prints the messages as the configured output format. */
fun Output.renderMessages(messages: List<Message>, styler: MessageStyler = MessageStyler.PLAIN) {
    table(MESSAGE_HEADERS, messages.map { messageRow(it, styler) })
}

/** The row for one [message]; pure so it can be snapshot-tested without an [Output]. */
internal fun messageRow(message: Message, styler: MessageStyler = MessageStyler.PLAIN): List<String> = listOf(
    message.id.toString(),
    DateTimeFormatter.ISO_INSTANT.format(message.sentAt),
    message.senderName,
    viaBotLabel(message),
    message.replyToMessageId?.toString().orEmpty(),
    message.media?.let(::mediaLabel).orEmpty(),
    message.action.orEmpty(),
    styler.style(message.text, message.entities),
)

/**
 * The inline bot a message came through: `@username` when the facade offers one, otherwise the
 * bare numeric id the facade projects, and blank when the message did not come via a bot. Pure, so
 * it can be snapshot-tested without an [Output].
 */
internal fun viaBotLabel(message: Message): String =
    message.viaBotUsername?.let { "@$it" } ?: message.viaBotId?.toString().orEmpty()

/**
 * The compact label for an attachment, driven by its kind: a video carries its duration and
 * dimensions, an audio clip its duration, a photo its dimensions and a document its size. A detail
 * the kind does not have is left out entirely rather than shown as a placeholder. Pure, so it can
 * be snapshot-tested without an [Output].
 */
internal fun mediaLabel(media: MediaInfo): String {
    val details = when (media.kind) {
        "video", "animation" -> listOfNotNull(media.durationLabel(), media.videoDimensionsLabel())
        "audio", "voice" -> listOfNotNull(media.durationLabel())
        "photo" -> listOfNotNull(media.dimensionsLabel())
        "document" -> listOfNotNull(media.sizeLabel())
        else -> emptyList()
    }
    return (listOf(media.kind) + details).joinToString(" ", prefix = "[", postfix = "]")
}

private fun MediaInfo.durationLabel(): String? = durationSeconds?.let(::formatDuration)

private fun MediaInfo.dimensionsLabel(): String? = dimensions(width, height)

/** A video's real resolution, falling back to the thumbnail's size when the resolution is absent. */
private fun MediaInfo.videoDimensionsLabel(): String? =
    dimensions(resolutionWidth, resolutionHeight) ?: dimensions(width, height)

private fun MediaInfo.sizeLabel(): String? = sizeBytes?.let(::formatSize)

private fun dimensions(width: Int?, height: Int?): String? =
    if (width != null && height != null) "${width}x$height" else null

/** `h:mm:ss` once the duration reaches an hour, `m:ss` below it, rounded to whole seconds. */
internal fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.roundToInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val remainder = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.twoDigits()}:${remainder.twoDigits()}"
    } else {
        "$minutes:${remainder.twoDigits()}"
    }
}

/** A binary size (`1024` per unit) with one decimal, e.g. `1.6 MB`; whole bytes below a kilobyte. */
internal fun formatSize(bytes: Long): String {
    var value = bytes.toDouble()
    var unit = 0
    while (value >= BYTES_PER_UNIT && unit < SIZE_UNITS.lastIndex) {
        value /= BYTES_PER_UNIT
        unit++
    }
    return if (unit == 0) "$bytes ${SIZE_UNITS[unit]}" else "${value.oneDecimal()} ${SIZE_UNITS[unit]}"
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

private fun Double.oneDecimal(): String =
    String.format(Locale.ROOT, "%.1f", this).removeSuffix(".0")

private const val BYTES_PER_UNIT = 1024.0

private val SIZE_UNITS = listOf("B", "KB", "MB", "GB", "TB")
