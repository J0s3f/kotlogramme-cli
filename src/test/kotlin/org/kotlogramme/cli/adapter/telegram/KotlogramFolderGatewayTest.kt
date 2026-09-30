package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.DialogFolder
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlogramFolderGatewayTest {
    @Test
    fun `asks the seam once and maps every folder to the domain`() {
        val operations = FakeFolderOperations().apply {
            folders = listOf(
                DialogFolder(
                    id = 2,
                    kind = "filter",
                    title = "News",
                    includePeers = listOf(peer(id = 7, kind = "user", name = "Ada")),
                ),
                DialogFolder(id = 0, kind = "default", title = ""),
            )
        }

        val folders = KotlogramFolderGateway(operations).folders()

        assertEquals(1, operations.calls)
        assertEquals(listOf(2, 0), folders.map { it.id })
        assertEquals(listOf("News", ""), folders.map { it.title })
        assertEquals(listOf("filter", "default"), folders.map { it.kind })
        assertEquals(listOf("Ada"), folders.first().includePeers.map { it.title })
    }
}

internal class FakeFolderOperations : FacadeFolderOperations {
    var folders: List<DialogFolder> = emptyList()
    var calls = 0

    override fun folders(): List<DialogFolder> {
        calls++
        return folders
    }
}
