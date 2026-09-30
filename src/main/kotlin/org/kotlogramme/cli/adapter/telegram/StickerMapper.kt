package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.StickerPack as FacadeStickerPack
import com.github.badoualy.telegram.api.StickerSet as FacadeStickerSet
import org.kotlogramme.cli.domain.StickerPack
import org.kotlogramme.cli.domain.StickerSet

/**
 * Reduces a facade sticker set to the domain projection a terminal client lists.
 *
 * The facade carries more than the domain keeps: text-colour and channel-emoji-status flags, the
 * creator, the installation date and the thumbnail identifiers are dropped, because nothing in the
 * CLI renders them. A pack keeps its emoticon and the sticker document ids it groups; the bytes are
 * fetched later through the normal media path, so only the ids cross this boundary.
 */
internal fun FacadeStickerSet.toStickerSet(): StickerSet = StickerSet(
    id = id,
    accessHash = accessHash,
    title = title,
    shortName = shortName,
    count = count,
    archived = archived,
    official = official,
    masks = masks,
    emojis = emojis,
    packs = packs.map { it.toStickerPack() },
    documents = documents,
)

/** Projects a facade pack's `documents` onto the domain's [StickerPack.documentIds]. */
internal fun FacadeStickerPack.toStickerPack(): StickerPack =
    StickerPack(emoticon = emoticon, documentIds = documents)
