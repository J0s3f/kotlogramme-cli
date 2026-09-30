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
    /** The facade's media kind, or `null` when the message has no media attached. */
    val mediaKind: String? = null,
)
