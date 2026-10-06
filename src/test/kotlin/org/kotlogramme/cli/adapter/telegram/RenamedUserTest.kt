package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.RawUpdate
import com.github.badoualy.telegram.api.TypedUpdate
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RenamedUserTest {
    private fun raw(name: String, hex: String) =
        TypedUpdate(kind = "raw", rawUpdate = RawUpdate(name, HexFormat.of().parseHex(hex)))

    @Test
    fun `an update that carries a new name names the user`() {
        // updateUserName user_id:291049397 first_name:"Ada" last_name:"" usernames:[]
        val payload = "248984a7" + "b50f591100000000" + "03416461" + "00000000" + "15c4b51c00000000"
        val renamed = raw("updateUserName", payload)

        assertEquals(291049397L, renamed.renamedUserId())
    }

    @Test
    fun `an update that only says a user changed names the user`() {
        val changed = raw("updateUser", "38945220" + "b50f591100000000")

        assertEquals(291049397L, changed.renamedUserId())
    }

    @Test
    fun `a status change is not a rename`() {
        val status = raw("updateUserStatus", "def8bde5b50f5911000000004939b9edec07c56a")

        assertNull(status.renamedUserId())
    }

    @Test
    fun `a message is not a rename`() {
        assertNull(TypedUpdate(kind = "newMessage", message = message(id = 1)).renamedUserId())
    }

    @Test
    fun `a rename whose payload cannot be decoded names nobody`() {
        assertNull(raw("updateUser", "3894522000").renamedUserId())
    }
}
