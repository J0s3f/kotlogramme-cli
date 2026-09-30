package org.kotlogramme.cli.adapter.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactMapperTest {
    @Test
    fun `maps every field when the user has a name, a username and a phone`() {
        val contact = user(
            id = 7,
            username = "ada",
            firstName = "Ada",
            lastName = "Lovelace",
            phone = "+1234",
        ).toContact()

        assertEquals(7L, contact.id)
        assertEquals("Ada Lovelace", contact.displayName)
        assertEquals("ada", contact.username)
        assertEquals("+1234", contact.phoneNumber)
    }

    @Test
    fun `falls back to the username when there is no name`() {
        val contact = user(id = 7, username = "ada").toContact()

        assertEquals("ada", contact.displayName)
        assertEquals("ada", contact.username)
        assertNull(contact.phoneNumber)
    }

    @Test
    fun `falls back to the id when there is neither name nor username`() {
        val contact = user(id = 7).toContact()

        assertEquals("7", contact.displayName)
        assertNull(contact.username)
        assertNull(contact.phoneNumber)
    }

    @Test
    fun `blank username and phone are dropped rather than rendered blank`() {
        val contact = user(id = 7, firstName = "Ada", username = "  ", phone = "").toContact()

        assertNull(contact.username)
        assertNull(contact.phoneNumber)
    }
}
