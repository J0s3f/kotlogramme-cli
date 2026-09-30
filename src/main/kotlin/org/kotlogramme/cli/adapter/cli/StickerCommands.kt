package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderMessages
import org.kotlogramme.cli.adapter.format.renderStickerSet
import org.kotlogramme.cli.adapter.format.renderStickerSets

/** Lists the account's sticker sets. */
class StickersCommand : CliktCommand(name = "stickers") {
    private val appContext by requireObject<AppContext>()

    override fun run() {
        appContext.output.renderStickerSets(rejectInvalidInput { appContext.stickers().sets() })
    }
}

/** Shows one sticker set, numbering the stickers so `send-sticker` can name one. */
class StickerSetCommand : CliktCommand(name = "sticker-set") {
    private val appContext by requireObject<AppContext>()

    private val set by argument("set", help = "The sticker set: a short name, or id:accessHash")

    override fun run() {
        appContext.output.renderStickerSet(rejectInvalidInput { appContext.stickers().set(set) })
    }
}

/** Sends one sticker of a set. */
class SendStickerCommand : CliktCommand(name = "send-sticker") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val set by argument("set", help = "The sticker set: a short name, or id:accessHash")
    private val index by argument("index", help = "The sticker's position, as `sticker-set` lists it").int()
    private val replyTo by option("--reply-to", help = "Reply to this message id").int()
    private val silent by option("--silent", help = "Send without a notification").flag()

    override fun run() {
        val message = rejectInvalidInput {
            appContext.stickers().send(peer, set, index, replyTo, silent)
        }
        appContext.output.renderMessages(listOf(message), appContext.messageStyler)
    }
}
