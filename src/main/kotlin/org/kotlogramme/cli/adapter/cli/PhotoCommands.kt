package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderFiles
import org.kotlogramme.cli.adapter.format.renderPhotos
import org.kotlogramme.cli.application.ListingCursor

/**
 * Lists the chat's own photo history.
 *
 * Telegram keeps the changes to a chat's profile photo as service messages, and this is that
 * timeline, not the photos posted in the chat: for those use `list-files --kind photo`, which asks
 * for the photo filter and reaches every photo the chat holds. The rows are ordinary messages, so
 * their ids can still be handed to `download-media`.
 */
class ChatPhotoHistoryCommand : CliktCommand(name = "chat-photo-history") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val limit by option("--limit", help = "How many photos to list").int().default(DEFAULT_LIMIT)
    private val after by option("--after", help = "The cursor to continue from, as the last page printed it")
    private val all by option(
        "--all",
        help = "List every photo in one call, ignoring the cursor and the limit",
    ).flag()

    override fun run() {
        val photos = rejectInvalidInput { appContext.photos().chatPhotos(peer, limit, after, all) }
        appContext.output.renderFiles(photos)
        if (photos.size == limit && !all) {
            appContext.output.line("# next: --after ${photos.last().id}")
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
    }
}

/**
 * Lists a user's profile photos.
 *
 * A profile photo is not a message, so the rows are the photo's own id and data centre rather than
 * a message id; the listing is the whole point, and no download is offered for one.
 */
class ProfilePhotosCommand : CliktCommand(name = "profile-photos") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The user: @username, numeric id or invite link")
    private val limit by option("--limit", help = "How many photos to list").int().default(DEFAULT_LIMIT)
    private val after by option("--after", help = "The cursor to continue from, as the last page printed it")
    private val all by option(
        "--all",
        help = "List every photo in one call, ignoring the cursor and the limit",
    ).flag()

    override fun run() {
        val photos = rejectInvalidInput { appContext.photos().profilePhotos(peer, limit, after, all) }
        appContext.output.renderPhotos(photos)
        if (photos.size == limit && !all) {
            val startingOffset = after?.let(ListingCursor::parseDecimal) ?: 0
            appContext.output.line("# next: --after ${startingOffset + photos.size}")
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
    }
}
