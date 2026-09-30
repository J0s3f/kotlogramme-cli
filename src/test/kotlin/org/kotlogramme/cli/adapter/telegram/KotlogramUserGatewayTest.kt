package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.User
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlogramUserGatewayTest {
    @Test
    fun `looks up the distinct ids once and maps each username`() {
        val operations = FakeUserOperations().apply {
            users = listOf(user(id = 99, username = "inline_bot"), user(id = 100, username = "other_bot"))
        }

        val usernames = KotlogramUserGateway(operations).usernames(listOf(99L, 100L, 99L))

        assertEquals(listOf(listOf(99L, 100L)), operations.lookups)
        assertEquals(mapOf(99L to "inline_bot", 100L to "other_bot"), usernames)
    }

    @Test
    fun `drops an account with no username and an id Telegram did not resolve`() {
        val operations = FakeUserOperations().apply {
            users = listOf(
                user(id = 99, username = "inline_bot"),
                user(id = 100, username = null),
                user(id = 101, username = "  "),
            )
        }

        val usernames = KotlogramUserGateway(operations).usernames(listOf(99L, 100L, 101L, 102L))

        assertEquals(mapOf(99L to "inline_bot"), usernames)
    }
}

internal class FakeUserOperations : FacadeUserOperations {
    var users: List<User> = emptyList()
    val lookups = mutableListOf<List<Long>>()

    override fun users(ids: List<Long>): List<User> {
        lookups += ids
        return users.filter { it.id in ids }
    }
}
