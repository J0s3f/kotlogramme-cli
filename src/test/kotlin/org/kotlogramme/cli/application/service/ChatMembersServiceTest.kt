package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.ParticipantGateway
import org.kotlogramme.cli.domain.Participant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChatMembersServiceTest {
    @Test
    fun `list reads through the gateway with the reference and limit`() {
        val gateway = FakeParticipantGateway().apply { members = listOf(ada) }

        val result = ChatMembersService(gateway).list("@club", limit = 20)

        assertEquals(listOf(MemberCall("@club", 20)), gateway.listCalls)
        assertEquals(listOf(ada), result)
    }

    @Test
    fun `kick passes both references through`() {
        val gateway = FakeParticipantGateway()

        ChatMembersService(gateway).kick("@club", "@ada")

        assertEquals(listOf(MemberKick("@club", "@ada")), gateway.kicks)
    }

    @Test
    fun `invite passes both references through`() {
        val gateway = FakeParticipantGateway()

        ChatMembersService(gateway).invite("@club", "@ada")

        assertEquals(listOf(MemberKick("@club", "@ada")), gateway.invites)
    }

    @Test
    fun `invite rejects a blank user before the gateway`() {
        val gateway = FakeParticipantGateway()
        val service = ChatMembersService(gateway)

        assertFailsWith<IllegalArgumentException> { service.invite("@club", "") }
        assertFailsWith<IllegalArgumentException> { service.invite("@club", "   ") }
        assertEquals(emptyList(), gateway.invites)
    }

    @Test
    fun `list passes the cursor to the gateway`() {
        val gateway = FakeParticipantGateway().apply { members = listOf(ada) }

        val result = ChatMembersService(gateway).list("@club", limit = 20, cursor = "10")

        assertEquals(listOf<String?>("10"), gateway.cursors)
        assertEquals(listOf(ada), result)
    }

    @Test
    fun `rejects a non-positive limit before the gateway`() {
        val gateway = FakeParticipantGateway()
        val service = ChatMembersService(gateway)

        assertFailsWith<IllegalArgumentException> { service.list("@club", 0) }
        assertFailsWith<IllegalArgumentException> { service.list("@club", -5) }
        assertEquals(emptyList(), gateway.listCalls)
    }

    @Test
    fun `rejects a blank user reference before the gateway`() {
        val gateway = FakeParticipantGateway()
        val service = ChatMembersService(gateway)

        assertFailsWith<IllegalArgumentException> { service.kick("@club", "") }
        assertFailsWith<IllegalArgumentException> { service.kick("@club", "   ") }
        assertEquals(emptyList(), gateway.kicks)
    }

    private class FakeParticipantGateway : ParticipantGateway {
        var members: List<Participant> = emptyList()
        val listCalls = mutableListOf<MemberCall>()
        val cursors = mutableListOf<String?>()
        val invites = mutableListOf<MemberKick>()
        val kicks = mutableListOf<MemberKick>()

        override fun participants(reference: String, limit: Int, cursor: String?): List<Participant> {
            listCalls += MemberCall(reference, limit)
            cursors += cursor
            return members
        }

        override fun invite(reference: String, userReference: String) {
            invites += MemberKick(reference, userReference)
        }

        override fun kick(reference: String, userReference: String) {
            kicks += MemberKick(reference, userReference)
        }
    }

    private data class MemberCall(val reference: String, val limit: Int)

    private data class MemberKick(val reference: String, val userReference: String)

    private companion object {
        val ada = Participant(id = 7, displayName = "Ada", username = "ada", role = "member")
    }
}
