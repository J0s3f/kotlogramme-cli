package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.Dialog
import com.github.badoualy.telegram.api.TelegramPeer
import org.kotlogramme.cli.domain.ChatKind
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KotlogramChatGatewayTest {
    private val now = Instant.parse("2026-09-30T12:00:00Z")

    @Test
    fun `dialogs prepends Saved Messages and skips folder rows`() {
        val operations = FakeChatOperations().apply {
            selfPeer = peer(id = 1, kind = "user", name = "Sam Self")
            dialogs = listOf(
                dialog(peer(id = 7, kind = "user", name = "Ada")),
                dialog(peer(id = 8, name = "News"), isFolder = true),
            )
        }

        val chats = KotlogramChatGateway(operations) { now }.dialogs(25)

        assertEquals(25, operations.lastDialogsLimit)
        assertEquals(listOf(1L, 7L), chats.map { it.id })
        assertEquals(1, operations.selfResolutions)
    }

    @Test
    fun `Saved Messages is the first row and is labelled stably`() {
        val operations = FakeChatOperations().apply {
            // The self peer's own name must not leak into the row.
            selfPeer = peer(id = 42, kind = "user", username = "sam", name = "Sam Self")
            dialogs = listOf(dialog(peer(id = 7, kind = "user", name = "Ada")))
        }

        val chats = KotlogramChatGateway(operations) { now }.dialogs(20)

        assertEquals("Saved Messages", chats.first().title)
        assertEquals(42L, chats.first().id)
        assertEquals(ChatKind.PRIVATE, chats.first().kind)
    }

    @Test
    fun `Saved Messages is an extra row beyond the requested dialogs`() {
        val operations = FakeChatOperations().apply {
            selfPeer = peer(id = 1)
            dialogs = (1..20).map { dialog(peer(id = it + 100L, name = "Chat $it")) }
        }

        val chats = KotlogramChatGateway(operations) { now }.dialogs(20)

        // `limit` still buys 20 real dialogs; Saved Messages does not consume one of the slots.
        assertEquals(21, chats.size)
        assertEquals(1L, chats.first().id)
    }

    @Test
    fun `Saved Messages is not duplicated when the listing also returns it`() {
        val operations = FakeChatOperations().apply {
            selfPeer = peer(id = 1, name = "Sam Self")
            dialogs = listOf(
                dialog(peer(id = 1, name = "Sam Self")),
                dialog(peer(id = 7, kind = "user", name = "Ada")),
            )
        }

        val chats = KotlogramChatGateway(operations) { now }.dialogs(20)

        assertEquals(listOf(1L, 7L), chats.map { it.id })
        assertEquals(1, chats.count { it.title == "Saved Messages" })
    }

    @Test
    fun `a subsequent page does not repeat Saved Messages`() {
        val operations = FakeChatOperations().apply {
            selfPeer = peer(id = 1, kind = "user", name = "Sam Self")
            dialogs = listOf(dialog(peer(id = 7, kind = "user", name = "Ada")))
        }

        val chats = KotlogramChatGateway(operations) { now }.dialogs(25, cursor = "1:5:1000", all = false)

        assertEquals(listOf(7L), chats.map { it.id })
        assertEquals(0, operations.selfResolutions)
    }

    @Test
    fun `--all lists Saved Messages once at the top`() {
        val operations = FakeChatOperations().apply {
            selfPeer = peer(id = 1, kind = "user", name = "Sam Self")
            dialogs = listOf(
                dialog(peer(id = 7, kind = "user", name = "Ada")),
                dialog(peer(id = 8, kind = "user", name = "Grace")),
            )
        }

        val chats = KotlogramChatGateway(operations) { now }.dialogs(25, all = true)

        assertEquals(listOf(1L, 7L, 8L), chats.map { it.id })
        assertEquals(1, operations.selfResolutions)
    }

    @Test
    fun `dialogs parses the cursor into the facade offset triple`() {
        val operations = FakeChatOperations().apply {
            selfPeer = peer(id = 1, kind = "user", name = "Sam Self")
            dialogs = listOf(dialog(peer(id = 7, kind = "user", name = "Ada")))
        }

        KotlogramChatGateway(operations) { now }.dialogs(25, cursor = "7:5:1000", all = false)

        assertEquals(7L, operations.lastOffsetPeer)
        assertEquals(5, operations.lastOffsetId)
        assertEquals(1000L, operations.lastOffsetDate)
        assertEquals(false, operations.lastAll)
    }

    @Test
    fun `dialogs rejects a malformed cursor`() {
        val operations = FakeChatOperations()

        assertFailsWith<IllegalArgumentException> {
            KotlogramChatGateway(operations) { now }.dialogs(25, cursor = "not-a-cursor", all = false)
        }
    }

    @Test
    fun `resolve reads a username`() {
        val operations = FakeChatOperations().apply {
            resolvedPeer = peer(id = 7, kind = "user", username = "ada", name = "Ada")
        }

        val chat = KotlogramChatGateway(operations) { now }.resolve("@ada")

        assertEquals(listOf("ada"), operations.resolvedUsernames)
        assertEquals("Ada", chat.title)
        assertEquals(ChatKind.PRIVATE, chat.kind)
    }

    @Test
    fun `resolve finds a numeric id in the dialog list`() {
        val operations = FakeChatOperations().apply {
            dialogs = listOf(dialog(peer(id = -9, kind = "group", name = "The Club")))
        }

        val chat = KotlogramChatGateway(operations) { now }.resolve("-9")

        assertEquals(ChatKind.GROUP, chat.kind)
        assertEquals("The Club", chat.title)
    }

    @Test
    fun `resolve rejects a numeric id that is not a dialog`() {
        val operations = FakeChatOperations()

        val error = assertFailsWith<IllegalArgumentException> {
            KotlogramChatGateway(operations) { now }.resolve("123")
        }

        assertTrue(error.message.orEmpty().contains("No chat with id 123"))
    }

    @Test
    fun `resolve imports a private invite link`() {
        val operations = FakeChatOperations().apply {
            inviteHash = "abc"
            importedPeer = peer(id = -9, kind = "channel", name = "Private", megagroup = true)
        }

        val chat = KotlogramChatGateway(operations) { now }.resolve("https://t.me/+abc")

        assertEquals(listOf("https://t.me/+abc"), operations.parsedLinks)
        assertEquals(listOf("https://t.me/+abc"), operations.importedLinks)
        assertEquals(ChatKind.SUPERGROUP, chat.kind)
    }

    @Test
    fun `resolve reads a public link as a username`() {
        val operations = FakeChatOperations().apply {
            resolvedPeer = peer(id = 7, username = "ada", name = "Ada")
        }

        val chat = KotlogramChatGateway(operations) { now }.resolve("https://t.me/ada")

        assertEquals(listOf("ada"), operations.resolvedUsernames)
        assertEquals("Ada", chat.title)
    }

    @Test
    fun `resolve rejects an unknown reference`() {
        val error = assertFailsWith<IllegalArgumentException> {
            KotlogramChatGateway(FakeChatOperations()) { now }.resolve("not a chat")
        }

        assertTrue(error.message.orEmpty().contains("Cannot resolve"))
    }

    @Test
    fun `resolve rejects a foreign link`() {
        assertFailsWith<IllegalArgumentException> {
            KotlogramChatGateway(FakeChatOperations()) { now }.resolve("https://example.com/ada")
        }
    }
}

internal class FakeChatOperations : FacadeChatOperations {
    var dialogs: List<Dialog> = emptyList()
    var lastDialogsLimit: Int? = null
    var lastOffsetPeer: Long? = null
    var lastOffsetId: Int? = null
    var lastOffsetDate: Long? = null
    var lastAll: Boolean = false
    var resolvedPeer: TelegramPeer? = null
    var inviteHash: String? = null
    var importedPeer: TelegramPeer? = null
    var peerById: TelegramPeer? = null
    /** The self peer `resolveSelf` answers; a plausible account so a test can prove the label. */
    var selfPeer: TelegramPeer = peer(id = 1, kind = "user", name = "Sam Self")
    val resolvedUsernames = mutableListOf<String>()
    val parsedLinks = mutableListOf<String>()
    val importedLinks = mutableListOf<String>()
    val resolvedIds = mutableListOf<Long>()
    var selfResolutions = 0

    override fun dialogs(limit: Int, offsetPeer: Long?, offsetId: Int?, offsetDate: Long?, all: Boolean): List<Dialog> {
        lastDialogsLimit = limit
        lastOffsetPeer = offsetPeer
        lastOffsetId = offsetId
        lastOffsetDate = offsetDate
        lastAll = all
        return dialogs
    }

    override fun resolveUsername(username: String): TelegramPeer {
        resolvedUsernames += username
        return resolvedPeer ?: error("no resolved peer configured")
    }

    override fun resolveSelf(): TelegramPeer {
        selfResolutions += 1
        return selfPeer
    }

    override fun parseInviteLink(link: String): String? {
        parsedLinks += link
        return inviteHash
    }

    override fun importChatInvite(link: String): TelegramPeer? {
        importedLinks += link
        return importedPeer
    }

    override fun resolvePeer(id: Long): TelegramPeer? {
        resolvedIds += id
        return peerById
    }
}
