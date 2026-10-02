package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.Participant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `members passes the cursor to the use case`() {
        val fake = FakeChatMembers()
        val fixture = cliFixture(chatMembers = fake)

        fixture.run("members", "@team", "--after", "10")

        assertEquals(listOf<String?>("10"), fake.cursors)
    }

    @Test
    fun `members prints the next cursor on a full page`() {
        val members = (1..50).map { index ->
            Participant(id = index.toLong(), displayName = "User $index", username = null, role = "member")
        }
        val fake = FakeChatMembers(members)
        val fixture = cliFixture(chatMembers = fake)

        val result = fixture.run("members", "@team")

        assertEquals(0, result.statusCode)
        assertTrue(fixture.output.text.contains("# next: --after 50"), fixture.output.text)
    }

    @Test
    fun `members omits the next cursor on a short page`() {
        val fake = FakeChatMembers(members)
        val fixture = cliFixture(chatMembers = fake)

        fixture.run("members", "@team")

        assertFalse(fixture.output.text.contains("# next:"), fixture.output.text)
    }

    @Test
    fun `members rejects a malformed cursor`() {
        val fake = FakeChatMembers()
        val fixture = cliFixture(chatMembers = fake)

        val result = fixture.run("members", "@team", "--after", "abc")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("malformed cursor"), "stderr was: ${result.stderr}")
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
