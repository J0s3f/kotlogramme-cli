package org.kotlogramme.cli.domain

/**
 * A dialog folder.
 *
 * [kind] is the facade's variant name (`filter`, `chatlist` or `default`). The three peer lists are
 * the folder's rules: peers always shown, peers added by rule, and peers excluded.
 */
data class Folder(
    val id: Int,
    val title: String,
    val kind: String,
    val pinnedPeers: List<Chat> = emptyList(),
    val includePeers: List<Chat> = emptyList(),
    val excludePeers: List<Chat> = emptyList(),
)
