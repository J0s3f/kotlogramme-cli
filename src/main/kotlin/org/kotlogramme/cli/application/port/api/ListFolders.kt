package org.kotlogramme.cli.application.port.api

import org.kotlogramme.cli.domain.Folder

/** List the account's dialog folders. */
interface ListFolders {
    fun list(): List<Folder>
}
