package org.kotlogramme.cli.domain

import java.time.Instant

/** A message as the terminal client renders it. */
data class Message(
    val id: Int,
    val senderName: String,
    val text: String,
    val sentAt: Instant,
    val outgoing: Boolean,
    val edited: Boolean = false,
    val pinned: Boolean = false,
    val replyToMessageId: Int? = null,
    /** What the attachment is, or `null` when the message has no media attached. */
    val media: MediaInfo? = null,
    /** A short human description of the service action, or `null` for an ordinary message. */
    val action: String? = null,
    /** The id of the inline bot this message came via, or `null` when it did not come via one. */
    val viaBotId: Long? = null,
    /**
     * The inline bot's username, when the facade offers one. The facade projects only [viaBotId],
     * so this is `null` today and the renderer falls back to the numeric id.
     */
    val viaBotUsername: String? = null,
)
