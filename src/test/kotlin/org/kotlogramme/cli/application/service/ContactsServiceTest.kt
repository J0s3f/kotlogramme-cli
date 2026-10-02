package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.ContactGateway
import org.kotlogramme.cli.domain.BlockedContact
import org.kotlogramme.cli.domain.Contact
import org.kotlogramme.cli.domain.ContactImportSummary
import org.kotlogramme.cli.domain.ContactToImport
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
    fun `blocked reads through the gateway with the limit`() {
        val gateway = FakeContactGateway()
        val service = ContactsService(gateway)

        service.blocked(limit = 20)

        assertEquals(listOf(20), gateway.blockedLimits)
    }

    @Test
    fun `blocked passes the cursor and all to the gateway`() {
        val gateway = FakeContactGateway()
        val service = ContactsService(gateway)

        service.blocked(limit = 20, cursor = "30", all = true)

        assertEquals(listOf<String?>("30"), gateway.blockedCursors)
        assertEquals(listOf(true), gateway.blockedAlls)
    }

    @Test
    fun `import passes the contacts through and answers the summary`() {
        val gateway = FakeContactGateway().apply {
            importSummary = ContactImportSummary(imported = listOf(ada), retryCount = 1)
        }
        val toImport = ContactToImport(phone = "+15550100", firstName = "Ada", lastName = "Lovelace")

        val summary = ContactsService(gateway).import(listOf(toImport))

        assertEquals(listOf(listOf(toImport)), gateway.imports)
        assertEquals(listOf(ada), summary.imported)
        assertEquals(1, summary.retryCount)
    }

    @Test
    fun `delete removes the reference through the gateway`() {
        val gateway = FakeContactGateway()
        val service = ContactsService(gateway)

        service.delete("@ada")

        assertEquals(listOf("@ada"), gateway.deleted)
    }

    @Test
    fun `rejects an empty or nameless import before the gateway`() {
        val gateway = FakeContactGateway()
        val service = ContactsService(gateway)

        assertFailsWith<IllegalArgumentException> { service.import(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            service.import(listOf(ContactToImport(phone = "", firstName = "Ada", lastName = "")))
        }
        assertFailsWith<IllegalArgumentException> {
            service.import(listOf(ContactToImport(phone = "+1", firstName = "", lastName = " ")))
        }
        assertEquals(emptyList(), gateway.imports)
    }

    @Test
    fun `rejects a blank delete reference before the gateway`() {
        val gateway = FakeContactGateway()

        assertFailsWith<IllegalArgumentException> { ContactsService(gateway).delete("  ") }
        assertEquals(emptyList(), gateway.deleted)
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
        var blockedContacts: List<BlockedContact> = emptyList()
        var importSummary: ContactImportSummary = ContactImportSummary(emptyList(), 0)
        val listCalls = mutableListOf<Int>()
        val searchCalls = mutableListOf<ContactSearchCall>()
        val blocked = mutableListOf<String>()
        val unblocked = mutableListOf<String>()
        val blockedLimits = mutableListOf<Int>()
        val blockedCursors = mutableListOf<String?>()
        val blockedAlls = mutableListOf<Boolean>()
        val imports = mutableListOf<List<ContactToImport>>()
        val deleted = mutableListOf<String>()

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

        override fun blocked(limit: Int, cursor: String?, all: Boolean): List<BlockedContact> {
            blockedLimits += limit
            blockedCursors += cursor
            blockedAlls += all
            return blockedContacts
        }

        override fun import(contacts: List<ContactToImport>): ContactImportSummary {
            imports += contacts
            return importSummary
        }

        override fun delete(reference: String) {
            deleted += reference
        }
    }

    private data class ContactSearchCall(val query: String, val limit: Int)

    private companion object {
        val ada = Contact(id = 7, displayName = "Ada", username = "ada", phoneNumber = "+1")
    }
}
