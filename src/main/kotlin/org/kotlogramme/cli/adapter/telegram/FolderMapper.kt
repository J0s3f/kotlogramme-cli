package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.DialogFolder
import org.kotlogramme.cli.domain.Folder

/**
 * Maps a facade dialog filter to the [Folder] the folder list shows.
 *
 * Each peer list goes through the same [toChat] projection the dialog list uses, so a folder's peers
 * render exactly like a dialog row. The layer splits a filter into three variants: a `filter` holds
 * rules and all three lists, a `chatlist` carries no excludes, and the `default` marker is an empty
 * id/title/peer holder. The mapper is a faithful projection, so a variant without a title crosses as
 * the empty title rather than an invented label.
 */
internal fun DialogFolder.toFolder(): Folder = Folder(
    id = id,
    title = title,
    kind = kind,
    pinnedPeers = pinnedPeers.map { it.toChat() },
    includePeers = includePeers.map { it.toChat() },
    excludePeers = excludePeers.map { it.toChat() },
)
