package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.spi.FolderGateway
import org.kotlogramme.cli.domain.Folder
import kotlin.test.Test
import kotlin.test.assertEquals

class ListFoldersServiceTest {
    @Test
    fun `lists the folders through the gateway`() {
        val gateway = FakeFolderGateway().apply { folders = listOf(folder) }

        val result = ListFoldersService(gateway).list()

        assertEquals(1, gateway.calls)
        assertEquals(listOf(folder), result)
    }

    private class FakeFolderGateway : FolderGateway {
        var calls = 0
        var folders: List<Folder> = emptyList()

        override fun folders(): List<Folder> {
            calls++
            return folders
        }
    }

    private companion object {
        val folder = Folder(id = 2, title = "News", kind = "filter")
    }
}
