package org.kotlogramme.cli.adapter.telegram

import com.github.badoualy.telegram.api.AllStickers
import com.github.badoualy.telegram.api.StickerSetResult

/**
 * The facade sticker calls the sticker gateway needs, narrowed to a seam a test can implement.
 *
 * This exists so [KotlogramStickerGateway] can be exercised without a live client. [set] mirrors
 * `messagesGetStickerSet`: the real facade takes a short name or an id/access-hash pair, so the
 * gateway parses the user's reference and fills the shape the facade expects. The incremental `hash`
 * is left at its default, because a full answer is what the gateway reports.
 */
internal interface FacadeStickerOperations {
    /** The account's installed sets, which is `messagesGetAllStickers`. */
    fun sets(): AllStickers

    /** One set by [shortName], or by [id] and [accessHash], which is `messagesGetStickerSet`. */
    fun set(id: Long?, accessHash: Long?, shortName: String?): StickerSetResult
}
