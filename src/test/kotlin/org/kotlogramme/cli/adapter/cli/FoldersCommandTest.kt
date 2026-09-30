package org.kotlogramme.cli.adapter.cli

import org.kotlogramme.cli.domain.Chat
import org.kotlogramme.cli.domain.ChatKind
import org.kotlogramme.cli.domain.Folder
import kotlin.test.Test
import kotlin.test.assertEquals

class FoldersCommandTest {
    private val work = Chat(
        id = 7,
        title = "Work",
        kind = ChatKind.GROUP,
        username = null,
        lastMessagePreview = null,
        lastMessageAt = null,
    )

    private val folders = listOf(
        Folder(id = 0, title = "All", kind = "default"),
        Folder(id = 1, title = "Work", kind = "filter", includePeers = listOf(work)),
    )

    @Test
    fun `folders renders the folder list with peer counts`() {
        val fixture = cliFixture(listFolders = FakeListFolders(folders))

        val result = fixture.run("folders")

        assertEquals(0, result.statusCode)
        assertEquals(
            listOf(
                "id\tkind\ttitle\tpinned\tincluded\texcluded",
                "0\tdefault\tAll\t0\t0\t0",
                "1\tfilter\tWork\t0\t1\t0",
            ),
            fixture.output.lines,
        )
    }
}
