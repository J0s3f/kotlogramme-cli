package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.ContactEntry
import com.github.badoualy.telegram.api.ContactPeer
import com.github.badoualy.telegram.api.ContactsPage
import com.github.badoualy.telegram.api.FoundContacts
import com.github.badoualy.telegram.api.TelegramPeer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KotlogramContactGatewayTest {
    @Test
    fun `contacts asks for the full list and applies the limit after mapping`() {
        val operations = FakeContactOperations().apply {
            page = ContactsPage(
                contacts = listOf(
                    ContactEntry(1, mutual = false, user = user(id = 1, username = "ada", firstName = "Ada"), peer = null),
                    ContactEntry(2, mutual = false, user = user(id = 2, firstName = "Grace", phone = "+1"), peer = null),
                    ContactEntry(3, mutual = false, user = user(id = 3, firstName = "Alan"), peer = null),
                ),
                savedCount = 3,
                notModified = false,
                users = emptyList(),
            )
        }

        val contacts = KotlogramContactGateway(operations).contacts(limit = 2)

        assertEquals(listOf(0L), operations.hashes)
        assertEquals(listOf(1L, 2L), contacts.map { it.id })
        assertEquals("Ada", contacts.first().displayName)
        assertEquals("ada", contacts.first().username)
        assertNull(contacts[1].username)
        assertEquals("+1", contacts[1].phoneNumber)
    }

    @Test
    fun `search forwards the query and limit and keeps both result sets`() {
        val adaPeer = peer(id = 1, kind = "user", username = "ada", name = "Ada")
        val gracePeer = peer(id = 2, kind = "user", name = "Grace")
        val operations = FakeContactOperations().apply {
            found = FoundContacts(
                myResults = listOf(ContactPeer(user(id = 1, username = "ada", firstName = "Ada"), adaPeer)),
                results = listOf(ContactPeer(user(id = 2, firstName = "Grace"), gracePeer)),
                users = emptyList(),
            )
        }

        val contacts = KotlogramContactGateway(operations).search("ad", limit = 25)

        assertEquals(listOf(ContactSearchCall("ad", 25)), operations.searches)
        assertEquals(listOf(1L, 2L), contacts.map { it.id })
        assertEquals(listOf("Ada", "Grace"), contacts.map { it.displayName })
    }

    @Test
    fun `block resolves the reference and blocks the resolved peer`() {
        val facadePeer = peer(id = 7, kind = "user", username = "ada", name = "Ada")
        val operations = FakeContactOperations().apply { resolvedPeer = facadePeer }

        KotlogramContactGateway(operations).block("@ada")

        assertEquals(listOf("@ada"), operations.resolvedReferences)
        assertEquals(listOf(facadePeer), operations.blocked)
    }

    @Test
    fun `unblock resolves the reference and unblocks the resolved peer`() {
        val facadePeer = peer(id = 7, kind = "user", username = "ada", name = "Ada")
        val operations = FakeContactOperations().apply { resolvedPeer = facadePeer }

        KotlogramContactGateway(operations).unblock("@ada")

        assertEquals(listOf("@ada"), operations.resolvedReferences)
        assertEquals(listOf(facadePeer), operations.unblocked)
    }
}

internal data class ContactSearchCall(val query: String, val limit: Int)

internal class FakeContactOperations : FacadeContactOperations {
    var page: ContactsPage = ContactsPage(emptyList(), 0, notModified = false, users = emptyList())
    var found: FoundContacts = FoundContacts(emptyList(), emptyList(), emptyList())
    var resolvedPeer: TelegramPeer? = null
    val hashes = mutableListOf<Long>()
    val searches = mutableListOf<ContactSearchCall>()
    val resolvedReferences = mutableListOf<String>()
    val blocked = mutableListOf<TelegramPeer>()
    val unblocked = mutableListOf<TelegramPeer>()

    override fun contacts(hash: Long): ContactsPage {
        hashes += hash
        return page
    }

    override fun search(query: String, limit: Int): FoundContacts {
        searches += ContactSearchCall(query, limit)
        return found
    }

    override fun resolve(reference: String): TelegramPeer {
        resolvedReferences += reference
        return resolvedPeer ?: error("no resolved peer configured")
    }

    override fun block(peer: TelegramPeer) {
        blocked += peer
    }

    override fun unblock(peer: TelegramPeer) {
        unblocked += peer
    }
}
