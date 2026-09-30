package org.kotlogramme.cli.application.port.spi

import org.kotlogramme.cli.domain.Folder

/** The dialog folder operations the facade exposes, in domain terms. */
interface FolderGateway {
    fun folders(): List<Folder>
}
