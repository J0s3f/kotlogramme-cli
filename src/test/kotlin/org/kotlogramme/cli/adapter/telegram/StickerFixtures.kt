package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.AllStickers
import com.github.badoualy.telegram.api.StickerPack
import com.github.badoualy.telegram.api.StickerSet
import com.github.badoualy.telegram.api.StickerSetResult

/** Builds facade sticker models for the adapter tests, so the mapping is exercised offline. */
internal fun stickerPack(emoticon: String, documents: List<Long>): StickerPack =
    StickerPack(emoticon = emoticon, documents = documents)

internal fun stickerSet(
    id: Long = 42,
    accessHash: Long = 99,
    title: String = "Cats",
    shortName: String = "cats",
    count: Int = 0,
    hash: Int = 0,
    archived: Boolean = false,
    official: Boolean = false,
    masks: Boolean = false,
    emojis: Boolean = false,
    packs: List<StickerPack> = emptyList(),
    documents: List<Long> = emptyList(),
): StickerSet = StickerSet(
    id = id,
    accessHash = accessHash,
    title = title,
    shortName = shortName,
    count = count,
    hash = hash,
    archived = archived,
    official = official,
    masks = masks,
    emojis = emojis,
    packs = packs,
    documents = documents,
)

internal fun allStickers(sets: List<StickerSet>, notModified: Boolean = false): AllStickers =
    AllStickers(notModified = notModified, hash = 0, sets = sets)

internal fun stickerSetResult(set: StickerSet?, notModified: Boolean = false): StickerSetResult =
    StickerSetResult(notModified = notModified, set = set)
