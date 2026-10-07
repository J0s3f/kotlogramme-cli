package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.RawUpdate
import com.github.badoualy.telegram.api.TypedUpdate
import org.kotlogramme.cli.domain.IncomingUpdate
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DecodedRawUpdateTest {
    private var decodes = 0

    private val userChanged = "38945220" + "b50f591100000000"

    private fun update(name: String) =
        TypedUpdate(kind = "raw", rawUpdate = RawUpdate(name, HexFormat.of().parseHex(userChanged)))

    private fun payloadOf(update: TypedUpdate) = DecodedRawUpdate(update.rawUpdate!!) { raw ->
        decodes++
        decodeUpdate(raw.data)
    }

    @Test
    fun `the payload is decoded once however many times it is read`() {
        val update = update("updateUser")
        val payload = payloadOf(update)

        update.renamedUserId(payload)
        update.renamedUserId(payload)
        update.toIncomingUpdate(payload = payload)

        assertEquals(1, decodes)
    }

    @Test
    fun `a payload that is never read is never decoded`() {
        payloadOf(update("updateUser"))

        assertEquals(0, decodes)
    }

    @Test
    fun `an update that is not a user change is not decoded to ask who changed`() {
        val update = update("updateReadHistoryInbox")

        update.renamedUserId(payloadOf(update))

        assertEquals(0, decodes)
    }

    @Test
    fun `the id read for the rename is the id the mapped update carries`() {
        val update = update("updateUser")
        val payload = payloadOf(update)

        val other = assertIs<IncomingUpdate.Other>(update.toIncomingUpdate(payload = payload))

        assertEquals(update.renamedUserId(payload), other.userId)
    }
}
