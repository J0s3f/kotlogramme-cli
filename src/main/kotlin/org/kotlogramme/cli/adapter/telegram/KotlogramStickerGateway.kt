package org.kotlogramme.cli.adapter.telegram

import org.kotlogramme.cli.application.port.spi.StickerGateway
import org.kotlogramme.cli.domain.StickerSet

/**
 * The [StickerGateway] backed by the kotlogramme facade.
 *
 * A reference is either a short name or `id:accessHash`, for a set the account has not installed.
 * The parsing happens here so the seam stays a mirror of the facade call.
 *
 * The incremental [com.github.badoualy.telegram.api.AllStickers.notModified] marker means the server
 * already had the answer for the given hash. The gateway always asks with hash 0, so it never
 * triggers the marker; if it ever does, `sets` reports an empty list because no data was carried,
 * while `set` raises a clear error because a single set has no useful empty projection.
 */
internal class KotlogramStickerGateway(private val operations: FacadeStickerOperations) : StickerGateway {
    override fun sets(): List<StickerSet> {
        val answer = operations.sets()
        if (answer.notModified) return emptyList()
        return answer.sets.map { it.toStickerSet() }
    }

    override fun set(reference: String): StickerSet {
        val parsed = parseReference(reference)
        val answer = operations.set(parsed.id, parsed.accessHash, parsed.shortName)
        val set = answer.set
        if (answer.notModified || set == null) {
            error("sticker set '$reference' came back not modified, so it carries no set")
        }
        return set.toStickerSet()
    }
}

/** A parsed reference: [shortName] for an installed set, or [id]/[accessHash] for any other. */
private data class StickerReference(
    val id: Long? = null,
    val accessHash: Long? = null,
    val shortName: String? = null,
)

private fun parseReference(reference: String): StickerReference {
    val separator = reference.indexOf(':')
    if (separator < 0) return StickerReference(shortName = reference)

    val id = reference.substring(0, separator).toLongOrNull()
    val accessHash = reference.substring(separator + 1).toLongOrNull()
    require(id != null && accessHash != null) {
        "reference '$reference' must be a short name or id:accessHash"
    }
    return StickerReference(id = id, accessHash = accessHash)
}
