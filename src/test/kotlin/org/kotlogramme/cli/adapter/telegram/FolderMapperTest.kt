package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.DialogFolder
import org.kotlogramme.cli.domain.ChatKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderMapperTest {
    @Test
    fun `maps a filter with all three peer lists`() {
        val folder = DialogFolder(
            id = 2,
            kind = "filter",
            title = "News",
            pinnedPeers = listOf(peer(id = 7, kind = "user", username = "ada", name = "Ada")),
            includePeers = listOf(peer(id = -9, kind = "group", name = "The Club")),
            excludePeers = listOf(peer(id = -1009, kind = "channel", name = "Noise")),
        ).toFolder()

        assertEquals(2, folder.id)
        assertEquals("News", folder.title)
        assertEquals("filter", folder.kind)
        assertEquals(listOf("Ada"), folder.pinnedPeers.map { it.title })
        assertEquals(listOf("The Club"), folder.includePeers.map { it.title })
        assertEquals(listOf("Noise"), folder.excludePeers.map { it.title })
        assertEquals(ChatKind.PRIVATE, folder.pinnedPeers.single().kind)
        assertEquals(ChatKind.GROUP, folder.includePeers.single().kind)
        assertEquals(ChatKind.CHANNEL, folder.excludePeers.single().kind)
    }

    @Test
    fun `maps a chatlist with no excludes`() {
        val folder = DialogFolder(
            id = 4,
            kind = "chatlist",
            title = "Chats",
            hasMyInvites = true,
            includePeers = listOf(peer(id = -1009, kind = "channel", name = "A Channel")),
        ).toFolder()

        assertEquals(4, folder.id)
        assertEquals("chatlist", folder.kind)
        assertEquals("Chats", folder.title)
        assertEquals(listOf("A Channel"), folder.includePeers.map { it.title })
        assertTrue(folder.pinnedPeers.isEmpty())
        assertTrue(folder.excludePeers.isEmpty())
    }

    @Test
    fun `maps the empty default marker`() {
        val folder = DialogFolder(id = 0, kind = "default", title = "").toFolder()

        assertEquals(0, folder.id)
        assertEquals("default", folder.kind)
        assertEquals("", folder.title)
        assertTrue(folder.pinnedPeers.isEmpty())
        assertTrue(folder.includePeers.isEmpty())
        assertTrue(folder.excludePeers.isEmpty())
    }

    @Test
    fun `keeps a missing title empty rather than inventing one`() {
        val folder = DialogFolder(
            id = 5,
            kind = "filter",
            title = "",
            groups = true,
        ).toFolder()

        assertEquals("", folder.title)
        assertEquals("filter", folder.kind)
    }
}
