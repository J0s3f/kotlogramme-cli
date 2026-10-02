package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Dialog
import com.github.badoualy.telegram.api.Message
import com.github.badoualy.telegram.api.TelegramPeer
import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import java.time.Instant

/**
 * The folder id Telegram uses for the archive.
 *
 * Its folder API only keeps the archive server-side; every other peer is in the main folder (id 0),
 * so a dialog that reports this id was archived. Custom folders never reach [Dialog.folderId].
 */
private const val ARCHIVE_FOLDER_ID = 1

/** Maps a facade dialog to the [Chat] the dialog list shows. */
internal fun Dialog.toChat(now: Instant): Chat = peer.toChat().copy(
    lastMessagePreview = lastMessage?.preview(),
    lastMessageAt = lastMessage?.date?.let(Instant::ofEpochMilli),
    unreadCount = unreadCount ?: 0,
    pinned = pinned,
    muted = isMuted(now),
    archived = folderId == ARCHIVE_FOLDER_ID,
)

/** Maps a resolved peer that has no dialog row, such as a username or invite lookup. */
internal fun TelegramPeer.toChat(): Chat = Chat(
    id = id,
    title = title(),
    kind = ChatKind.fromFacade(kind, isMegagroup),
    username = username,
    lastMessagePreview = null,
    lastMessageAt = null,
)

/** The label the dialog list always shows for the private chat with yourself. */
internal const val SAVED_MESSAGES_TITLE = "Saved Messages"

/**
 * Maps the self peer to the fixed Saved Messages row.
 *
 * The self peer's own name is the account holder's first and last name, which changes with the
 * profile and reads like a contact rather than the special conversation it is. The label is fixed
 * to [SAVED_MESSAGES_TITLE] so the row is recognisable whatever the account is called.
 */
internal fun TelegramPeer.toSavedMessagesChat(): Chat = toChat().copy(title = SAVED_MESSAGES_TITLE, isSelf = true)

private fun TelegramPeer.title(): String =
    name?.takeIf(String::isNotBlank) ?: username ?: id.toString()

/**
 * The dialog is muted while its notification mute runs into the future.
 *
 * [Dialog] metadata reports `muteUntil` in epoch milliseconds: `0` when notifications are on and a
 * far-future instant for "muted forever", so comparing against [now] covers both a timed and a
 * permanent mute. The metadata is only asked for by a listing that reads it; without it the dialog
 * is treated as unmuted.
 */
private fun Dialog.isMuted(now: Instant): Boolean =
    (notifySettings?.muteUntil ?: 0L) > now.toEpochMilli()

/** The one-line dialog preview: the text, or a bracketed media kind when the message carries none. */
private fun Message.preview(): String? =
    text.takeIf(String::isNotBlank) ?: media?.kind?.let { "[$it]" }
