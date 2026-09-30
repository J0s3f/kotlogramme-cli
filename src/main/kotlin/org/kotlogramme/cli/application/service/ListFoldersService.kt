package org.kotlogramme.cli.application.service

import org.kotlogramme.cli.application.port.api.ListFolders
import org.kotlogramme.cli.application.port.spi.FolderGateway
import org.kotlogramme.cli.domain.Folder

/** Lists the account's dialog folders through the gateway. */
class ListFoldersService(private val gateway: FolderGateway) : ListFolders {
    override fun list(): List<Folder> = gateway.folders()
}
