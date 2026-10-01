package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.BlockedContact
import org.kotlogramme.cli.domain.Contact
import org.kotlogramme.cli.domain.ContactToImport
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactsCommandTest {
    private val contacts = listOf(Contact(id = 1, displayName = "Ada", username = "ada", phoneNumber = "+15550100"))

    @Test
    fun `contacts lists with the default limit`() {
        val fake = FakeContacts(contacts)
        val fixture = cliFixture(contacts = fake)

        val result = fixture.run("contacts")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(50), fake.limits)
        assertEquals(
            listOf(
                "id\tname\tusername\tphone",
                "1\tAda\tada\t+15550100",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `contacts asks for the requested limit`() {
        val fake = FakeContacts(contacts)
        val fixture = cliFixture(contacts = fake)

        fixture.run("contacts", "--limit", "5")

        assertEquals(listOf(5), fake.limits)
    }

    @Test
    fun `search-contacts passes the query and limit and renders the matches`() {
        val fake = FakeContacts(searchResults = contacts)
        val fixture = cliFixture(contacts = fake)

        val result = fixture.run("search-contacts", "ada", "--limit", "5")

        assertEquals(0, result.statusCode)
        assertEquals(listOf("ada" to 5), fake.searches)
        assertEquals(
            listOf(
                "id\tname\tusername\tphone",
                "1\tAda\tada\t+15550100",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `block and unblock name the peer`() {
        val fake = FakeContacts()
        val fixture = cliFixture(contacts = fake)

        fixture.run("block", "@ada")
        fixture.run("unblock", "@ada")

        assertEquals(listOf("@ada"), fake.blocked)
        assertEquals(listOf("@ada"), fake.unblocked)
    }

    @Test
    fun `blocked lists with the default limit and renders the rows`() {
        val fake = FakeContacts(
            blockedContacts = listOf(
                BlockedContact(
                    id = 1,
                    displayName = "Ada",
                    username = "ada",
                    blockedAt = Instant.parse("2026-01-01T12:30:00Z"),
                ),
            ),
        )
        val fixture = cliFixture(contacts = fake)

        val result = fixture.run("blocked")

        assertEquals(0, result.statusCode)
        assertEquals(listOf(50), fake.blockedLimits)
        assertEquals(
            listOf(
                "id\tname\tusername\tblocked",
                "1\tAda\tada\t2026-01-01T12:30:00Z",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `import-contacts builds one contact and reports the save`() {
        val fake = FakeContacts(importedContacts = contacts)
        val fixture = cliFixture(contacts = fake)

        val result = fixture.run("import-contacts", "+15550100", "Ada", "Lovelace")

        assertEquals(0, result.statusCode)
        assertEquals(
            listOf(listOf(ContactToImport("+15550100", "Ada", "Lovelace"))),
            fake.imports,
        )
        assertEquals(listOf("Imported 1 of 1 contact(s)."), fixture.output.lines)
    }

    @Test
    fun `import-contacts reports a retry Telegram asked for`() {
        val fake = FakeContacts(retryCount = 1)
        val fixture = cliFixture(contacts = fake)

        fixture.run("import-contacts", "+15550100", "Ada")

        assertEquals(
            listOf(
                "Imported 0 of 1 contact(s).",
                "Telegram asked to retry 1 contact(s).",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `delete-contact says the peer is not blocked`() {
        val fake = FakeContacts()
        val fixture = cliFixture(contacts = fake)

        fixture.run("delete-contact", "@ada")

        assertEquals(listOf("@ada"), fake.deleted)
        assertEquals(listOf("Deleted @ada from contacts. The peer is not blocked."), fixture.output.lines)
    }
}
