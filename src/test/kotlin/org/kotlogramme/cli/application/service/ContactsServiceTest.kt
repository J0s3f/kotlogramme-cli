package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.ContactGateway
import org.kotlogramme.cli.domain.Contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContactsServiceTest {
    @Test
    fun `list reads through the gateway with the limit`() {
        val gateway = FakeContactGateway().apply { contacts = listOf(ada) }

        val result = ContactsService(gateway).list(limit = 20)

        assertEquals(listOf(20), gateway.listCalls)
        assertEquals(listOf(ada), result)
    }

    @Test
    fun `search passes the query and limit through`() {
        val gateway = FakeContactGateway().apply { searchResults = listOf(ada) }

        val result = ContactsService(gateway).search("ada", limit = 5)

        assertEquals(listOf(ContactSearchCall("ada", 5)), gateway.searchCalls)
        assertEquals(listOf(ada), result)
    }

    @Test
    fun `block and unblock go through the gateway`() {
        val gateway = FakeContactGateway()
        val service = ContactsService(gateway)

        service.block("@ada")
        service.unblock("@ada")

        assertEquals(listOf("@ada"), gateway.blocked)
        assertEquals(listOf("@ada"), gateway.unblocked)
    }

    @Test
    fun `rejects a non-positive limit before the gateway`() {
        val gateway = FakeContactGateway()
        val service = ContactsService(gateway)

        assertFailsWith<IllegalArgumentException> { service.list(0) }
        assertFailsWith<IllegalArgumentException> { service.list(-5) }
        assertFailsWith<IllegalArgumentException> { service.search("ada", 0) }
        assertFailsWith<IllegalArgumentException> { service.search("ada", -1) }
        assertEquals(emptyList(), gateway.listCalls)
        assertEquals(emptyList(), gateway.searchCalls)
    }

    @Test
    fun `rejects a blank search query before the gateway`() {
        val gateway = FakeContactGateway()
        val service = ContactsService(gateway)

        assertFailsWith<IllegalArgumentException> { service.search("", 10) }
        assertFailsWith<IllegalArgumentException> { service.search("   ", 10) }
        assertEquals(emptyList(), gateway.searchCalls)
    }

    private class FakeContactGateway : ContactGateway {
        var contacts: List<Contact> = emptyList()
        var searchResults: List<Contact> = emptyList()
        val listCalls = mutableListOf<Int>()
        val searchCalls = mutableListOf<ContactSearchCall>()
        val blocked = mutableListOf<String>()
        val unblocked = mutableListOf<String>()

        override fun contacts(limit: Int): List<Contact> {
            listCalls += limit
            return contacts
        }

        override fun search(query: String, limit: Int): List<Contact> {
            searchCalls += ContactSearchCall(query, limit)
            return searchResults
        }

        override fun block(reference: String) {
            blocked += reference
        }

        override fun unblock(reference: String) {
            unblocked += reference
        }
    }

    private data class ContactSearchCall(val query: String, val limit: Int)

    private companion object {
        val ada = Contact(id = 7, displayName = "Ada", username = "ada", phoneNumber = "+1")
    }
}
