package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.BlockedContacts
import com.github.badoualy.telegram.api.ContactImport
import com.github.badoualy.telegram.api.ContactsPage
import com.github.badoualy.telegram.api.FoundContacts
import com.github.badoualy.telegram.api.ImportedContacts
import com.github.badoualy.telegram.api.TelegramClient
import com.github.badoualy.telegram.api.TelegramPeer

/** The real [FacadeContactOperations], delegating straight to the facade client. */
internal class KotlogramContactOperations(private val client: TelegramClient) : FacadeContactOperations {
    private val resolver = ChatReferenceResolver(KotlogramChatOperations(client))

    override fun contacts(hash: Long): ContactsPage = client.contactsGetContacts(hash)

    override fun search(query: String, limit: Int): FoundContacts = client.contactsSearch(q = query, limit = limit)

    override fun resolve(reference: String): TelegramPeer = resolver.resolve(reference)

    override fun block(peer: TelegramPeer) = client.contactsBlock(peer)

    override fun unblock(peer: TelegramPeer) = client.contactsUnblock(peer)

    override fun blocked(offset: Int, limit: Int, all: Boolean): BlockedContacts =
        client.contactsGetBlocked(offset = offset, limit = limit, all = all)

    override fun importContacts(contacts: List<ContactImport>): ImportedContacts =
        client.contactsImportContacts(contacts)

    override fun deleteContacts(peers: List<TelegramPeer>) = client.contactsDeleteContacts(peers)
}
