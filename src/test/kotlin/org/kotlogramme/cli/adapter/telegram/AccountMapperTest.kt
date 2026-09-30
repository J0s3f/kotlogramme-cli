package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountMapperTest {
    @Test
    fun `maps every account field`() {
        val user = user(
            id = 42L,
            username = "j0s3f",
            firstName = "Jo",
            lastName = "Sef",
            phone = "+3312345",
        )

        val account = user.toAccount()

        assertEquals(42L, account.id)
        assertEquals("Jo", account.firstName)
        assertEquals("Sef", account.lastName)
        assertEquals("j0s3f", account.username)
        assertEquals("+3312345", account.phoneNumber)
    }

    @Test
    fun `keeps a missing username and phone absent`() {
        val account = user(id = 7L, username = null, firstName = "Jo", lastName = "Sef", phone = null).toAccount()

        assertNull(account.username)
        assertNull(account.phoneNumber)
    }

    @Test
    fun `maps missing names to empty strings`() {
        val account = user(id = 7L, username = "j0s3f", firstName = null, lastName = null).toAccount()

        assertEquals("", account.firstName)
        assertEquals("", account.lastName)
    }

    @Test
    fun `derives the display name from the username when names are blank`() {
        val account = user(id = 7L, username = "j0s3f", firstName = "", lastName = "").toAccount()

        assertEquals("j0s3f", account.displayName)
    }

    @Test
    fun `falls back to the id for the display name when nothing else is set`() {
        val account = user(id = 7L, username = null, firstName = null, lastName = null).toAccount()

        assertEquals("7", account.displayName)
    }

    private fun user(
        id: Long,
        username: String? = null,
        firstName: String? = null,
        lastName: String? = null,
        phone: String? = null,
    ): User = User(
        id = id,
        username = username,
        firstName = firstName,
        lastName = lastName,
        phone = phone,
    )
}
