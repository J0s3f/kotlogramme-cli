package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Dialog
import com.github.badoualy.telegram.api.TelegramPeer

/**
 * The facade dialog and peer calls the chat gateway needs, narrowed to a seam a test can implement.
 *
 * This exists so [KotlogramChatGateway] and [KotlogramMessageGateway] can be exercised without a
 * live client. Each method mirrors one facade operation; the facade models are mapped to the domain
 * at the gateway boundary.
 */
internal interface FacadeChatOperations {
    /**
     * The dialogs with their metadata, which is what `messagesGetDialogsMeta` answers; the plain
     * listing projection carries no notification settings, so mute and folder state would be lost.
     *
     * [offsetPeer]/[offsetId]/[offsetDate] continue from a previous page, as the cursor the command
     * printed names them; [all] walks the whole set in one call and wins over them and [limit].
     */
    fun dialogs(
        limit: Int,
        offsetPeer: Long? = null,
        offsetId: Int? = null,
        offsetDate: Long? = null,
        all: Boolean = false,
    ): List<Dialog>

    /** Resolves a bare username, which is `contactsResolveUsername`. */
    fun resolveUsername(username: String): TelegramPeer

    /**
     * Resolves the peer the session's account is itself, which is `getSelfPeer`: the private chat
     * with yourself, addressed as `inputPeerSelf` rather than as a chat id.
     *
     * It never appears in `messages.getDialogs`, so no dialog row can reach it and
     * `contactsResolveUsername` would look for a user *named* "me" instead.
     */
    fun resolveSelf(): TelegramPeer

    /** The hash of a private invite link, or null for a public link, which is `messagesParseInviteLink`. */
    fun parseInviteLink(link: String): String?

    /** Joins a private chat from its invite link, which is `messagesImportChatInvite`. */
    fun importChatInvite(link: String): TelegramPeer?

    /**
     * Resolves a Bot API dialog id from what the session already knows, which is
     * `channelsResolvePeer`; `null` when the session has never seen that peer.
     */
    fun resolvePeer(id: Long): TelegramPeer?
}
