package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.Contact
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
}
