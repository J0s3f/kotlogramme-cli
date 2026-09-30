package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ChatPermissions
import com.github.badoualy.telegram.api.Dialog
import org.kotlogramme.protocol.ChatPermissions as ProtocolChatPermissions
import com.github.badoualy.telegram.api.DialogNotifySettings
import com.github.badoualy.telegram.api.Media
import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramPeer
import com.github.badoualy.telegram.api.User
import org.kotlogramme.protocol.MessageAction
import org.kotlogramme.protocol.Peer
import java.lang.reflect.Constructor

/**
 * Builds facade models for the adapter tests.
 *
 * The facade marks the `TelegramPeer` and `Peer` constructors `internal`, so a consumer module
 * cannot call them from Kotlin even though their JVM constructors are public. These fixtures reflect
 * those two constructors so the mapping can be exercised offline, without a live client.
 */
internal fun peer(
    id: Long,
    kind: String = "user",
    username: String? = null,
    name: String? = null,
    megagroup: Boolean? = null,
): TelegramPeer {
    val native = peerConstructor().newInstance(
        0L, id, kind, username, name, emptyList<String>(), megagroup, false, null,
    )
    return telegramPeerConstructor().newInstance(
        id, kind, username, name, native, emptyList<String>(), megagroup, false, null,
    ) as TelegramPeer
}

internal fun user(
    id: Long,
    username: String? = null,
    firstName: String? = null,
    lastName: String? = null,
    phone: String? = null,
): User = User(
    id = id,
    username = username,
    firstName = firstName,
    lastName = lastName,
    phone = phone,
)

internal fun dialog(
    peer: TelegramPeer,
    lastMessage: Message? = null,
    pinned: Boolean = false,
    unreadCount: Int? = 0,
    folderId: Int? = null,
    isFolder: Boolean = false,
    muteUntil: Long? = null,
): Dialog = Dialog(
    peer = peer,
    lastMessage = lastMessage,
    pinned = pinned,
    unreadCount = unreadCount,
    folderId = folderId,
    isFolder = isFolder,
    notifySettings = muteUntil?.let { DialogNotifySettings(muteUntil = it) },
)

internal fun message(
    id: Int,
    text: String = "",
    outgoing: Boolean = false,
    replyToMessageId: Int? = null,
    date: Long = 0L,
    editDate: Long? = null,
    pinned: Boolean = false,
    media: Media? = null,
    postAuthor: String? = null,
    sender: User? = null,
    peer: TelegramPeer? = null,
    action: MessageAction? = null,
): Message = Message(
    id = id,
    text = text,
    outgoing = outgoing,
    replyToMessageId = replyToMessageId,
    date = date,
    editDate = editDate,
    pinned = pinned,
    media = media,
    postAuthor = postAuthor,
    sender = sender,
    peer = peer,
    action = action,
)

private fun telegramPeerConstructor(): Constructor<*> = TelegramPeer::class.java.getDeclaredConstructor(
    Long::class.javaPrimitiveType,
    String::class.java,
    String::class.java,
    String::class.java,
    Peer::class.java,
    List::class.java,
    Boolean::class.javaObjectType,
    Boolean::class.javaPrimitiveType,
    ChatPermissions::class.java,
)

private fun peerConstructor(): Constructor<*> = Peer::class.java.getDeclaredConstructor(
    Long::class.javaPrimitiveType,
    Long::class.javaPrimitiveType,
    String::class.java,
    String::class.java,
    String::class.java,
    List::class.java,
    Boolean::class.javaObjectType,
    Boolean::class.javaPrimitiveType,
    ProtocolChatPermissions::class.java,
)
