package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ContactsPage
import com.github.badoualy.telegram.api.FoundContacts
import com.github.badoualy.telegram.api.TelegramPeer

/**
 * The facade contact calls the contact gateway needs, narrowed to a seam a test can implement.
 *
 * This exists so [KotlogramContactGateway] can be exercised without a live client. Each method
 * mirrors one facade operation, except [resolve], which delegates to the shared
 * [ChatReferenceResolver] so a `@username`, an invite link and a numeric id mean the same peer here
 * as they do elsewhere in the adapter.
 */
internal interface FacadeContactOperations {
    /** Lists the account's saved contacts, which is `contactsGetContacts`. */
    fun contacts(hash: Long): ContactsPage

    /** Searches saved contacts and the public directory, which is `contactsSearch`. */
    fun search(query: String, limit: Int): FoundContacts

    /** Resolves a user-supplied reference to a facade peer. */
    fun resolve(reference: String): TelegramPeer

    /** Blocks [peer], which is `contactsBlock`. */
    fun block(peer: TelegramPeer)

    /** Unblocks [peer], which is `contactsUnblock`. */
    fun unblock(peer: TelegramPeer)
}
