package org.kotlogramme.cli.adapter.format

import org.kotlogramme.cli.application.port.spi.Output
import org.kotlogramme.cli.domain.StickerSet

/** The columns of the sticker-set list. */
internal val STICKER_SET_HEADERS = listOf("id", "short name", "title", "count", "flags")

/** Prints the installed sticker sets as the configured output format. */
fun Output.renderStickerSets(sets: List<StickerSet>) {
    table(STICKER_SET_HEADERS, sets.map(::stickerSetRow))
}

/** The row for one set; pure so it can be snapshot-tested without an [Output]. */
internal fun stickerSetRow(set: StickerSet): List<String> = listOf(
    set.id.toString(),
    set.shortName,
    set.title,
    set.count.toString(),
    stickerFlags(set),
)

/** The set's flags as one comma-separated cell, empty when it has none. */
internal fun stickerFlags(set: StickerSet): String = buildList {
    if (set.official) add("official")
    if (set.masks) add("masks")
    if (set.emojis) add("emoji")
    if (set.archived) add("archived")
}.joinToString(",")

/**
 * Prints one set with its stickers numbered.
 *
 * The index column is what `send-sticker` takes, so it has to be the order the layer reported the
 * documents in, which is [StickerSet.documents].
 */
fun Output.renderStickerSet(set: StickerSet) {
    line("${set.title} (${set.shortName})")
    line("id ${set.id} · ${set.count} stickers · ${stickerFlags(set)}")
    table(listOf("index", "document", "emoticon"), stickerRows(set), title = "Stickers")
}

/** One row per document: its index, its id, and the emoticon its pack files it under. */
internal fun stickerRows(set: StickerSet): List<List<String>> {
    val emoticons = set.packs
        .flatMap { pack -> pack.documentIds.map { it to pack.emoticon } }
        .toMap()
    return set.documents.mapIndexed { index, documentId ->
        listOf(index.toString(), documentId.toString(), emoticons[documentId].orEmpty())
    }
}
