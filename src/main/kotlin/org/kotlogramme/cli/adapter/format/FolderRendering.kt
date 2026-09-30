package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.Folder

/**
 * Renders the dialog folder list.
 *
 * The row is plain data so the same projection feeds every [Output] format; the three peer rules
 * become explicit counts rather than being folded into the title.
 */
internal val FOLDER_HEADERS = listOf("id", "kind", "title", "pinned", "included", "excluded")

/** Prints the folders as the configured output format. */
fun Output.renderFolders(folders: List<Folder>) {
    table(FOLDER_HEADERS, folders.map(::folderRow))
}

/** The row for one [folder]; pure so it can be snapshot-tested without an [Output]. */
internal fun folderRow(folder: Folder): List<String> = listOf(
    folder.id.toString(),
    folder.kind,
    folder.title,
    folder.pinnedPeers.size.toString(),
    folder.includePeers.size.toString(),
    folder.excludePeers.size.toString(),
)
