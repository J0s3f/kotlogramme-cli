package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.AdminRightsGateway
import org.kotlogramme.cli.domain.ChatRestrictions
import org.kotlogramme.cli.domain.ChatRights
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminRightsServiceTest {
    @Test
    fun `permissions forwards valid references and returns the rights`() {
        val gateway = RecordingAdminRightsGateway()

        val rights = AdminRightsService(gateway).permissions("@club", "@ada")

        assertEquals(ChatRights.ALL, rights)
        assertEquals(listOf("permissions:@club:@ada"), gateway.calls)
    }

    @Test
    fun `promote forwards the rights`() {
        val gateway = RecordingAdminRightsGateway()

        AdminRightsService(gateway).promote("@club", "@ada", ChatRights.NONE)

        assertEquals(listOf("setAdmin:@club:@ada"), gateway.calls)
    }

    @Test
    fun `restrict forwards the restrictions`() {
        val gateway = RecordingAdminRightsGateway()

        AdminRightsService(gateway).restrict("@club", "@ada", ChatRestrictions.NONE_ALLOWED)

        assertEquals(listOf("setBanned:@club:@ada"), gateway.calls)
    }

    @Test
    fun `a blank chat reference is rejected before the gateway`() {
        val gateway = RecordingAdminRightsGateway()
        val service = AdminRightsService(gateway)

        assertFailsWith<IllegalArgumentException> { service.permissions("  ", "@ada") }
        assertFailsWith<IllegalArgumentException> { service.promote("", "@ada", ChatRights.NONE) }
        assertFailsWith<IllegalArgumentException> { service.restrict("", "@ada", ChatRestrictions.NONE_ALLOWED) }

        assertEquals(emptyList(), gateway.calls)
    }

    @Test
    fun `a blank user reference is rejected before the gateway`() {
        val gateway = RecordingAdminRightsGateway()
        val service = AdminRightsService(gateway)

        assertFailsWith<IllegalArgumentException> { service.permissions("@club", " ") }
        assertFailsWith<IllegalArgumentException> { service.promote("@club", "", ChatRights.NONE) }
        assertFailsWith<IllegalArgumentException> { service.restrict("@club", "", ChatRestrictions.NONE_ALLOWED) }

        assertEquals(emptyList(), gateway.calls)
    }
}

/** An [AdminRightsGateway] that records every call it receives. */
private class RecordingAdminRightsGateway : AdminRightsGateway {
    val calls = mutableListOf<String>()

    override fun permissions(reference: String, userReference: String): ChatRights {
        calls += "permissions:$reference:$userReference"
        return ChatRights.ALL
    }

    override fun setAdmin(reference: String, userReference: String, rights: ChatRights) {
        calls += "setAdmin:$reference:$userReference"
    }

    override fun setBanned(reference: String, userReference: String, restrictions: ChatRestrictions) {
        calls += "setBanned:$reference:$userReference"
    }
}
