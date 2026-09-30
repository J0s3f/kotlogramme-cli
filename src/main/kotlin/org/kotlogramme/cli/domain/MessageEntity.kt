package org.kotlogramme.cli.domain

/**
 * One formatting span on a message's text.
 *
 * [kind] is the layer's entity constructor name without its `messageEntity` prefix (`bold`, `pre`,
 * `textUrl`, `mentionName`, `customEmoji`, …). [offset] and [length] are in UTF-16 code units, the
 * unit Telegram and Kotlin strings both use, so they index [Message.text] directly.
 * [url], [userId], [language] and [customEmojiId] are the extras only the kinds that accept them
 * carry; the rest are left null.
 */
data class MessageEntity(
    val kind: String,
    val offset: Int,
    val length: Int,
    val url: String? = null,
    val userId: Long? = null,
    val language: String? = null,
    val customEmojiId: Long? = null,
)
