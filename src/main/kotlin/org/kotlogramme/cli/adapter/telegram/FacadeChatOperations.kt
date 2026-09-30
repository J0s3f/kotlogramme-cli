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
     */
    fun dialogs(limit: Int): List<Dialog>

    /** Resolves a bare username, which is `contactsResolveUsername`. */
    fun resolveUsername(username: String): TelegramPeer

    /** The hash of a private invite link, or null for a public link, which is `messagesParseInviteLink`. */
    fun parseInviteLink(link: String): String?

    /** Joins a private chat from its invite link, which is `messagesImportChatInvite`. */
    fun importChatInvite(link: String): TelegramPeer?
}
