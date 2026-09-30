package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Message as FacadeMessage
import com.github.badoualy.telegram.api.User
import org.kotlogramme.cli.domain.Message
import java.time.Instant

/**
 * Reduces a facade message to what a terminal client renders.
 *
 * The facade reports [FacadeMessage.date] in epoch milliseconds (the native bridge converts the
 * wire's unix seconds with `Timestamp::as_millisecond`), so it is turned into an [Instant] with
 * [Instant.ofEpochMilli] and not `ofEpochSecond`.
 */
internal fun FacadeMessage.toMessage(): Message = Message(
    id = id,
    senderName = senderName(),
    text = text,
    sentAt = Instant.ofEpochMilli(date),
    outgoing = outgoing,
    edited = editDate != null,
    pinned = pinned,
    replyToMessageId = replyToMessageId,
    mediaKind = media?.kind,
)

private fun FacadeMessage.senderName(): String =
    sender?.displayName()
        ?: postAuthor?.takeIf(String::isNotBlank)
        ?: peer?.name?.takeIf(String::isNotBlank)
        ?: ""

private fun User.displayName(): String = listOf(firstName.orEmpty(), lastName.orEmpty())
    .filter(String::isNotBlank)
    .joinToString(" ")
    .ifBlank { username ?: id.toString() }
