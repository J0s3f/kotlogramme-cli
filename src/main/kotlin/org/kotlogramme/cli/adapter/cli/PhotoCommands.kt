package org.kotlogramme.cli.adapter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import org.kotlogramme.cli.adapter.format.renderFiles
import org.kotlogramme.cli.adapter.format.renderPhotos

/**
 * Lists a chat's photo messages.
 *
 * A chat photo is an ordinary message, so the rows carry its id and can be handed to
 * `download-media`; `list-files --kind photo` lists every photo a chat holds, while this asks the
 * library for the chat-photo timeline specifically.
 */
class ChatPhotosCommand : CliktCommand(name = "chat-photos") {
    private val appContext by requireObject<AppContext>()

    private val peer by argument("peer", help = "The chat: @username, numeric id or invite link")
    private val limit by option("--limit", help = "How many photos to list").int().default(DEFAULT_LIMIT)

    override fun run() {
        appContext.output.renderFiles(
            rejectInvalidInput { appContext.photos().chatPhotos(peer, limit) },
        )
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

    override fun run() {
        appContext.output.renderPhotos(
            rejectInvalidInput { appContext.photos().profilePhotos(peer, limit) },
        )
    }

    private companion object {
        const val DEFAULT_LIMIT = 50
    }
}
