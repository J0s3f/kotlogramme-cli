package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.Participant
import kotlin.test.Test
import kotlin.test.assertEquals

class MembersCommandTest {
    private val members = listOf(Participant(id = 1, displayName = "Ada", username = "ada", role = "creator"))

    @Test
    fun `members lists with the default limit`() {
        val fake = FakeChatMembers(members)
        val fixture = cliFixture(chatMembers = fake)

        val result = fixture.run("members", "@team")

        assertEquals(0, result.statusCode)
        assertEquals(MemberCall("@team", 50), fake.lists.single())
        assertEquals(
            listOf(
                "id\tname\tusername\trole",
                "1\tAda\tada\tcreator",
            ),
            fixture.output.lines,
        )
    }

    @Test
    fun `members asks for the requested limit`() {
        val fake = FakeChatMembers(members)
        val fixture = cliFixture(chatMembers = fake)

        fixture.run("members", "@team", "--limit", "5")

        assertEquals(MemberCall("@team", 5), fake.lists.single())
    }

    @Test
    fun `kick removes the named member from the chat`() {
        val fake = FakeChatMembers()
        val fixture = cliFixture(chatMembers = fake)

        val result = fixture.run("kick", "@team", "@ada")

        assertEquals(0, result.statusCode)
        assertEquals(listOf("@team" to "@ada"), fake.kicks)
        assertEquals(listOf("Kicked @ada from @team."), fixture.output.lines)
    }
}
