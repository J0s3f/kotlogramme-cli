package org.kotlogramme.cli.adapter.mcp.tool

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A read only tool must not be able to join a chat. The resolver behind a chat reference joins on an invite
 * link, so those never get through; only references that name an existing chat are accepted.
 */
class ChatParameterTest {
    private val parameter = ChatParameter("chat", "The chat.")

    @Test
    fun `me a username and a numeric id are accepted`() {
        for (reference in listOf("me", "@me", "@ada_lovelace", "@Ada123", "42", "-1001234567890")) {
            assertEquals(reference, parameter.parse(JsonPrimitive(reference)), reference)
        }
    }

    @Test
    fun `links of any kind are refused because an invite link joins the chat`() {
        for (reference in listOf(
            "https://t.me/+AbCdEf123",
            "https://t.me/joinchat/AbCdEf",
            "http://t.me/ada",
            "https://telegram.me/ada",
            "t.me/+AbCdEf123",
            "tg://join?invite=AbCdEf",
        )) {
            val problem = assertFailsWith<IllegalArgumentException>(reference) { parameter.parse(JsonPrimitive(reference)) }

            assertTrue(problem.message!!.contains("@username"), problem.message)
        }
    }

    @Test
    fun `other text is refused with the accepted forms`() {
        for (reference in listOf("Ada Lovelace", "ada", "@", "@a b", "@bad-name", "12abc")) {
            assertFailsWith<IllegalArgumentException>(reference) { parameter.parse(JsonPrimitive(reference)) }
        }
    }

    @Test
    fun `the schema is a required string that explains the accepted forms`() {
        val schema = ChatParameter("chat").schema()

        assertEquals(JsonPrimitive("string"), schema["type"])
        assertTrue((schema["description"] as JsonPrimitive).content.contains("@username"))
    }
}
