package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Dialog
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramPeer

/** The real [FacadeChatOperations], delegating straight to the facade client. */
internal class KotlogramChatOperations(private val client: TelegramClient) : FacadeChatOperations {
    override fun dialogs(limit: Int): List<Dialog> = client.messagesGetDialogsMeta(limit)

    override fun resolveUsername(username: String): TelegramPeer = client.contactsResolveUsername(username)

    override fun parseInviteLink(link: String): String? = client.messagesParseInviteLink(link)

    override fun importChatInvite(link: String): TelegramPeer? = client.messagesImportChatInvite(link)

    override fun resolvePeer(id: Long): TelegramPeer? = client.channelsResolvePeer(id)
}
