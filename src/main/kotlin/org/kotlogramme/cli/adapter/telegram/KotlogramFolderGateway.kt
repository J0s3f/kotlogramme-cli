package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.FolderGateway
import org.kotlogramme.cli.domain.Folder

/** The [FolderGateway] backed by the kotlogramme facade. */
internal class KotlogramFolderGateway(
    private val operations: FacadeFolderOperations,
) : FolderGateway {
    override fun folders(): List<Folder> = operations.folders().map { it.toFolder() }
}
