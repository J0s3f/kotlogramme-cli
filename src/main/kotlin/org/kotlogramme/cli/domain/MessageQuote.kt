package org.kotlogramme.cli.domain

/**
 * The text a reply quotes, with its own formatting entities.
 *
 * This is the subset of the facade's quote shape the terminal renders: the [text] and the
 * [entities] on it. The facade also carries its own `htmlText` and `markdownText` renderings, but
 * they belong to the writer, not the reader — the client styles the entities itself, with colour on
 * a terminal and plain otherwise — so they are not carried into the domain. Like the rest of the
 * domain it keeps no facade type, only the primitive fields it needs, exactly as [Message.entities]
 * does for a message's own text.
 */
data class MessageQuote(
    /** The quoted text, as the reply header carries it. */
    val text: String,
    /** The formatting entities on [text], empty when the text is unformatted. */
    val entities: List<MessageEntity> = emptyList(),
)
