package org.kotlogramme.cli.domain

/** One emoticon of a sticker set and the sticker documents filed under it. */
data class StickerPack(
    val emoticon: String,
    val documentIds: List<Long>,
)

/**
 * A sticker set.
 *
 * [accessHash] comes with [id] so the set can be addressed again without a second lookup. The
 * stickers themselves travel as document ids, which is what the layer reports; the bytes are
 * fetched through the normal media path.
 */
data class StickerSet(
    val id: Long,
    val accessHash: Long,
    val title: String,
    val shortName: String,
    val count: Int,
    val archived: Boolean = false,
    val official: Boolean = false,
    val masks: Boolean = false,
    val emojis: Boolean = false,
    val packs: List<StickerPack> = emptyList(),
    /** The documents the answer carried, in the order Telegram returned them; `send` indexes this. */
    val documents: List<Long> = emptyList(),
) {
    /** How many stickers this projection actually carries, which is not always [count]. */
    val projectedCount: Int
        get() = documents.ifEmpty { packs.flatMap { it.documentIds } }.size
}
